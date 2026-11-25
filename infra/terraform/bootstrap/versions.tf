terraform {
  # S3 백엔드의 자체 잠금(use_lockfile)이 1.10부터라 그 이상을 쓴다.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project   = "tikkle"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}
