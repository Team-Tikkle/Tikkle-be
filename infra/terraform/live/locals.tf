# 계정 번호는 레포에 적지 않고 로그인한 계정에서 읽는다.
data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = "ap-northeast-2"

  # 4-6에서 만들 자원의 이름. 역할 정책이 미리 이 이름으로 범위를 좁힌다.
  ecr_repository_name = "tikkle-be"
  ecr_repository_arn  = "arn:aws:ecr:${local.region}:${local.account_id}:repository/${local.ecr_repository_name}"

  # 앱 비밀값은 SSM Parameter Store의 이 경로 아래에만 둔다 (4-5).
  ssm_parameter_path = "/tikkle/prod"

  github_repository = "Team-Tikkle/Tikkle-be"
}
