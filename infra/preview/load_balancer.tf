resource "aws_lb" "preview" {
  name               = "file-drive-preview"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.load_balancer.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "web" {
  name        = "file-drive-preview-web"
  port        = 80
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.preview.id

  health_check {
    enabled = true
    path    = "/health"
    matcher = "200"
  }
}

resource "aws_lb_target_group" "api" {
  name        = "file-drive-preview-api"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.preview.id

  health_check {
    enabled = true
    path    = "/api/auth/me"
    matcher = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.preview.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}
