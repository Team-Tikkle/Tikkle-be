# 이 구성의 상태도 자기가 만든 버킷에 둔다.
# 처음 만들 때는 버킷이 없으므로 이 파일 없이 로컬 상태로 적용하고, 그 뒤 이 파일을 두고
#   terraform init -migrate-state -backend-config=../backend.hcl
# 로 상태를 버킷으로 옮겼다. 버킷이 없는 계정에서 처음부터 다시 만들 때도 같은 순서를 따른다.
terraform {
  backend "s3" {
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
