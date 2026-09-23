data "aws_caller_identity" "current" {}

locals {
  storage_buckets = toset([
    "files",
    "lockbox",
    "quarantine",
    "trash",
  ])
}

resource "aws_s3_bucket" "storage" {
  for_each = local.storage_buckets

  bucket = "file-drive-preview-${each.key}-${data.aws_caller_identity.current.account_id}-eu-central-1"
}

resource "aws_s3_bucket_public_access_block" "storage" {
  for_each = aws_s3_bucket.storage

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.storage]
}

data "aws_iam_policy_document" "api_storage" {
  statement {
    sid = "ListApplicationBuckets"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
      "s3:ListBucketMultipartUploads",
    ]
    resources = [for bucket in aws_s3_bucket.storage : bucket.arn]
  }

  statement {
    sid = "ManageApplicationObjects"
    actions = [
      "s3:AbortMultipartUpload",
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:ListMultipartUploadParts",
      "s3:PutObject",
    ]
    resources = [for bucket in aws_s3_bucket.storage : "${bucket.arn}/*"]
  }
}

resource "aws_iam_role_policy" "api_storage" {
  name   = "file-drive-preview-storage"
  role   = aws_iam_role.api_task.id
  policy = data.aws_iam_policy_document.api_storage.json
}
