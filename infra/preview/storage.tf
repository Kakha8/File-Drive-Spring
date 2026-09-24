data "aws_caller_identity" "current" {}

locals {
  storage_buckets = toset([
    "files",
    "lockbox",
    "quarantine",
    "trash",
  ])
}

resource "aws_kms_key" "storage" {
  description             = "Encrypts File Drive preview objects in S3"
  enable_key_rotation     = true
  deletion_window_in_days = 30

  tags = {
    Name = "file-drive-preview-storage"
  }
}

resource "aws_kms_alias" "storage" {
  name          = "alias/file-drive-preview-storage"
  target_key_id = aws_kms_key.storage.key_id
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
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.storage.arn
      sse_algorithm     = "aws:kms"
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

  statement {
    sid = "UseStorageKeyThroughS3"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey",
      "kms:ReEncryptFrom",
      "kms:ReEncryptTo",
    ]
    resources = [aws_kms_key.storage.arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["s3.eu-central-1.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:aws:s3:arn"
      values = concat(
        [for bucket in aws_s3_bucket.storage : bucket.arn],
        [for bucket in aws_s3_bucket.storage : "${bucket.arn}/*"],
      )
    }
  }
}

resource "aws_iam_role_policy" "api_storage" {
  name   = "file-drive-preview-storage"
  role   = aws_iam_role.api_task.id
  policy = data.aws_iam_policy_document.api_storage.json
}
