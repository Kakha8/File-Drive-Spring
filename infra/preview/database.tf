resource "aws_db_subnet_group" "preview" {
  name       = "file-drive-preview"
  subnet_ids = aws_subnet.database[*].id
}

resource "aws_db_instance" "preview" {
  identifier                  = "file-drive-preview"
  engine                      = "postgres"
  instance_class              = "db.t4g.micro"
  allocated_storage           = 20
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "filedrive"
  username                    = "filedrive_admin"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.preview.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 1
  deletion_protection         = false
  skip_final_snapshot         = true
  apply_immediately           = true
}

resource "random_password" "api_admin" {
  length  = 40
  special = false
}

resource "random_password" "api_jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "api_runtime" {
  name                    = "file-drive/preview/api-runtime"
  description             = "Generated preview admin and JWT signing secrets"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "api_runtime" {
  secret_id = aws_secretsmanager_secret.api_runtime.id
  secret_string = jsonencode({
    ADMIN_PASSWORD = random_password.api_admin.result
    JWT_SECRET     = random_password.api_jwt.result
  })
}
