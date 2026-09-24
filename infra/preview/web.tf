resource "aws_cloudwatch_log_group" "web" {
  name              = "/ecs/file-drive-preview-web"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "web" {
  family                   = "file-drive-preview-web"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([{
    name      = "web"
    image     = var.web_container_image
    essential = true
    portMappings = [{
      containerPort = 80
      protocol      = "tcp"
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.web.name
        awslogs-region        = "eu-central-1"
        awslogs-stream-prefix = "web"
      }
    }
  }])
}

resource "aws_ecs_service" "web" {
  name            = "file-drive-preview-web"
  cluster         = aws_ecs_cluster.preview.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = var.web_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.web.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = 80
  }

  depends_on = [aws_lb_listener.http]
}
