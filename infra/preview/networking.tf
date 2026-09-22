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

resource "aws_security_group" "api" {
  name        = "file-drive-preview-api"
  description = "Preview API access from one IPv4 address"
  vpc_id      = aws_vpc.preview.id

  ingress {
    description = "API from developer IP"
    from_port   = 8443
    to_port     = 8443
    protocol    = "tcp"
    cidr_blocks = [var.allowed_api_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
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
