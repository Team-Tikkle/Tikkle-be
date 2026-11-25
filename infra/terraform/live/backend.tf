# 상태는 bootstrap 구성이 만든 S3 버킷에 둔다.
# 버킷 이름에는 계정 번호가 들어가서 레포에 적지 않는다. 처음 한 번:
#   terraform init -backend-config=../backend.hcl
# ../backend.hcl (infra/terraform/backend.hcl, gitignore 대상) 내용:
#   bucket = "<bootstrap 출력 state_bucket>"
terraform {
  backend "s3" {
    key          = "live/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
