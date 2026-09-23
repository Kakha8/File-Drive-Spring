output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "web_ecr_repository_url" {
  value = aws_ecr_repository.web.repository_url
}

output "application_url" {
  value = "http://${aws_lb.preview.dns_name}"
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.preview.name
}

output "ecs_service_name" {
  value = aws_ecs_service.api.name
}

output "database_endpoint" {
  value = aws_db_instance.preview.endpoint
}

output "database_secret_arn" {
  value = aws_db_instance.preview.master_user_secret[0].secret_arn
}

output "storage_bucket_names" {
  description = "Private S3 buckets used by the preview API."
  value = {
    for purpose, bucket in aws_s3_bucket.storage : purpose => bucket.bucket
  }
}
