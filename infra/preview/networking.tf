data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "preview" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "file-drive-preview" }
}

resource "aws_internet_gateway" "preview" {
  vpc_id = aws_vpc.preview.id
  tags   = { Name = "file-drive-preview" }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.preview.id
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  cidr_block              = cidrsubnet(aws_vpc.preview.cidr_block, 8, count.index)
  map_public_ip_on_launch = true

  tags = { Name = "file-drive-preview-public-${count.index + 1}" }
}

resource "aws_subnet" "database" {
  count             = 2
  vpc_id            = aws_vpc.preview.id
  availability_zone = data.aws_availability_zones.available.names[count.index]
  cidr_block        = cidrsubnet(aws_vpc.preview.cidr_block, 8, count.index + 10)

  tags = { Name = "file-drive-preview-db-${count.index + 1}" }
}

resource "aws_subnet" "application" {
  count             = 2
  vpc_id            = aws_vpc.preview.id
  availability_zone = data.aws_availability_zones.available.names[count.index]
  cidr_block        = cidrsubnet(aws_vpc.preview.cidr_block, 8, count.index + 2)

  tags = { Name = "file-drive-preview-app-${count.index + 1}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.preview.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.preview.id
  }

  tags = { Name = "file-drive-preview-public" }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# One NAT gateway keeps the preview cost below a redundant per-AZ design. The
# API task needs limited internet egress for ClamAV signature updates. Interface
# and gateway endpoints keep supported AWS-service traffic off the NAT path.
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = { Name = "file-drive-preview-nat" }
}

resource "aws_nat_gateway" "preview" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = { Name = "file-drive-preview" }

  depends_on = [aws_internet_gateway.preview]
}

resource "aws_route_table" "application" {
  vpc_id = aws_vpc.preview.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.preview.id
  }

  tags = { Name = "file-drive-preview-application" }
}

resource "aws_route_table_association" "application" {
  count          = 2
  subnet_id      = aws_subnet.application[count.index].id
  route_table_id = aws_route_table.application.id
}

resource "aws_security_group" "api" {
  name_prefix = "file-drive-preview-api-"
  description = "Private preview API task"
  vpc_id      = aws_vpc.preview.id

  lifecycle {
    create_before_destroy = true
  }

}

resource "aws_security_group" "load_balancer" {
  name        = "file-drive-preview-alb"
  description = "Public HTTP access to the preview application"
  vpc_id      = aws_vpc.preview.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "web" {
  name        = "file-drive-preview-web"
  description = "Preview web traffic from the load balancer"
  vpc_id      = aws_vpc.preview.id

  ingress {
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.load_balancer.id]
  }

}

resource "aws_security_group" "vpc_endpoints" {
  name        = "file-drive-preview-vpc-endpoints"
  description = "HTTPS from preview ECS tasks to interface VPC endpoints"
  vpc_id      = aws_vpc.preview.id
}

resource "aws_security_group_rule" "endpoints_from_api" {
  type                     = "ingress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.vpc_endpoints.id
  source_security_group_id = aws_security_group.api.id
}

resource "aws_security_group_rule" "endpoints_from_web" {
  type                     = "ingress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.vpc_endpoints.id
  source_security_group_id = aws_security_group.web.id
}

resource "aws_security_group_rule" "api_to_endpoints" {
  type                     = "egress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.api.id
  source_security_group_id = aws_security_group.vpc_endpoints.id
}

resource "aws_security_group_rule" "web_to_endpoints" {
  type                     = "egress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.web.id
  source_security_group_id = aws_security_group.vpc_endpoints.id
}

data "aws_prefix_list" "s3" {
  name = "com.amazonaws.eu-central-1.s3"
}

resource "aws_security_group_rule" "api_to_s3" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  security_group_id = aws_security_group.api.id
  prefix_list_ids   = [data.aws_prefix_list.s3.id]
}

resource "aws_security_group_rule" "web_to_s3" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  security_group_id = aws_security_group.web.id
  prefix_list_ids   = [data.aws_prefix_list.s3.id]
}

# The API and ClamAV containers share one Fargate task ENI. These rules are
# therefore scoped to the API task and to web ports, but cannot distinguish the
# ClamAV sidecar from the Java process. A filtering proxy is required for
# domain-level allowlisting.
resource "aws_security_group_rule" "api_clamav_http" {
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  security_group_id = aws_security_group.api.id
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "api_clamav_https" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  security_group_id = aws_security_group.api.id
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "api_from_load_balancer" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_security_group.api.id
  source_security_group_id = aws_security_group.load_balancer.id
}

resource "aws_security_group" "database" {
  name        = "file-drive-preview-db"
  description = "PostgreSQL from preview API only"
  vpc_id      = aws_vpc.preview.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id]
  }
}

resource "aws_security_group_rule" "api_to_database" {
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.api.id
  source_security_group_id = aws_security_group.database.id
}

locals {
  interface_vpc_endpoints = toset([
    "ecr.api",
    "ecr.dkr",
    "logs",
    "secretsmanager",
  ])
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_vpc_endpoints

  vpc_id              = aws_vpc.preview.id
  service_name        = "com.amazonaws.eu-central-1.${each.value}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = aws_subnet.application[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = { Name = "file-drive-preview-${replace(each.value, ".", "-")}" }
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.preview.id
  service_name      = "com.amazonaws.eu-central-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.application.id]

  tags = { Name = "file-drive-preview-s3" }
}
