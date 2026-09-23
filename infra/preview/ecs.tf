resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/file-drive-preview-api"
  retention_in_days = 7
}

data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_execution" {
  name               = "file-drive-preview-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "read-preview-runtime-secrets"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_db_instance.preview.master_user_secret[0].secret_arn,
        aws_secretsmanager_secret.api_runtime.arn
      ]
    }]
  })
}

resource "aws_iam_role" "api_task" {
  name               = "file-drive-preview-api-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_ecs_cluster" "preview" {
  name = "file-drive-preview"
}

resource "aws_ecs_task_definition" "api" {
  family                   = "file-drive-preview-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 3072
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.api_task.arn

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = var.container_image
      essential = true
      cpu       = 512
      dependsOn = [{
        containerName = "clamav"
        condition     = "HEALTHY"
      }]
      portMappings = [{
        containerPort = 8080
        protocol      = "tcp"
      }]
      secrets = [
        {
          name      = "DB_PASSWORD"
          valueFrom = "${aws_db_instance.preview.master_user_secret[0].secret_arn}:password::"
        },
        {
          name      = "ADMIN_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.api_runtime.arn}:ADMIN_PASSWORD::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.api_runtime.arn}:JWT_SECRET::"
        }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws-preview" },
        { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.preview.address}:${aws_db_instance.preview.port}/${aws_db_instance.preview.db_name}" },
        { name = "DB_USERNAME", value = aws_db_instance.preview.username },
        { name = "AUTH_COOKIE_SECURE", value = "false" },
        { name = "JWT_REFRESH_DAYS", value = "14" },
        { name = "QUARANTINE_RETENTION_DAYS", value = "30" },
        { name = "CLAMAV_HOST", value = "127.0.0.1" },
        { name = "CLAMAV_PORT", value = "3310" },
        { name = "CLAMAV_TIMEOUT_MS", value = "120000" },
        { name = "SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE", value = "500MB" },
        { name = "SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE", value = "500MB" },
        { name = "S3_ENDPOINT", value = "https://s3.eu-central-1.amazonaws.com" },
        { name = "S3_REGION", value = "eu-central-1" },
        { name = "S3_USE_IAM_ROLE", value = "true" },
        { name = "S3_BUCKET", value = aws_s3_bucket.storage["files"].bucket },
        { name = "S3_LOCKBOX_BUCKET", value = aws_s3_bucket.storage["lockbox"].bucket },
        { name = "S3_QUARANTINE_BUCKET", value = aws_s3_bucket.storage["quarantine"].bucket },
        { name = "S3_TRASH_BUCKET", value = aws_s3_bucket.storage["trash"].bucket }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = "eu-central-1"
          awslogs-stream-prefix = "api"
        }
      }
    },
    {
      name      = "clamav"
      image     = var.clamav_image
      essential = true
      cpu       = 512
      environment = [
        { name = "CLAMD_CONF_StreamMaxLength", value = "500M" },
        { name = "CLAMD_CONF_MaxFileSize", value = "500M" },
        { name = "CLAMD_CONF_MaxScanSize", value = "500M" }
      ]
      portMappings = [{
        containerPort = 3310
        protocol      = "tcp"
      }]
      healthCheck = {
        command     = ["CMD-SHELL", "/usr/local/bin/clamdcheck.sh"]
        interval    = 30
        timeout     = 10
        retries     = 5
        startPeriod = 300
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = "eu-central-1"
          awslogs-stream-prefix = "clamav"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "api" {
  name            = "file-drive-preview-api"
  cluster         = aws_ecs_cluster.preview.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener_rule.api]
}
