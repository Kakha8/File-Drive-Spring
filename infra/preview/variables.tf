variable "container_image" {
  description = "Backend image URI. Keep the service at zero tasks until an AWS preview image has been pushed to ECR."
  type        = string
  default     = "public.ecr.aws/docker/library/eclipse-temurin:21-jre"
}

variable "desired_count" {
  description = "Number of preview API tasks. Set to 1 after pushing the application image."
  type        = number
  default     = 0

  validation {
    condition     = var.desired_count >= 0 && floor(var.desired_count) == var.desired_count
    error_message = "desired_count must be a non-negative whole number."
  }
}

variable "web_container_image" {
  description = "React/Nginx image URI. Keep the web service at zero tasks until its image has been pushed to ECR."
  type        = string
  default     = "public.ecr.aws/nginx/nginx:stable-alpine"
}

variable "web_desired_count" {
  description = "Number of preview web tasks. Set to 1 after pushing the web image."
  type        = number
  default     = 0

  validation {
    condition     = var.web_desired_count >= 0 && floor(var.web_desired_count) == var.web_desired_count
    error_message = "web_desired_count must be a non-negative whole number."
  }
}
