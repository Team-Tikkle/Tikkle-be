# 앱 비밀값 중 새로 만드는 2개. 적용할 때 AWS가 난수를 만들고 SSM에 write-only로만 넣는다
# (Terraform 상태에 남지 않고, 사람도 보지 않는다).
#
# 사람이 가진 옛 값 7개는 Terraform이 관리하지 않는다. infra/terraform/scripts/put-app-secrets.ps1 로 넣는다:
#   PAYMENT_SECRET_KEY, GEMINI_API_KEY, DEEPSEEK_API_KEY,
#   COOLSMS_API_KEY, COOLSMS_API_SECRET, COOLSMS_SENDER_NUMBER, FCM_CREDENTIALS_BASE64
# DB 접속 정보 3개는 ssm_db.tf. 모두 /tikkle/prod/<환경 변수 이름> 이다.

# JWT 서명 키. 앱은 문자열 바이트를 그대로 키로 쓰고 길이로 알고리즘을 고른다 — 64바이트면 HS512.
ephemeral "aws_secretsmanager_random_password" "jwt_secret" {
  password_length     = 64
  exclude_punctuation = true
  include_space       = false
}

# 업비트 키 암호화 키(AES-256). 앱은 UTF-8로 정확히 32바이트가 아니면 기동하지 않는다.
ephemeral "aws_secretsmanager_random_password" "encrypt_key" {
  password_length     = 32
  exclude_punctuation = true
  include_space       = false
}

locals {
  # 바꿀 때 1씩 올린다.
  # JWT를 바꾸면 발급된 모든 토큰이 무효가 된다(전원 다시 로그인).
  jwt_secret_version = 1
  # AES를 바꾸면 DB에 암호화해 둔 업비트 키를 풀 수 없게 된다. 재암호화 없이 올리지 않는다.
  encrypt_key_version = 1
}

resource "aws_ssm_parameter" "jwt_secret" {
  name             = "${local.ssm_parameter_path}/JWT_SECRET"
  description      = "Tikkle JWT signing key (generated, 64 chars, write-only)"
  type             = "SecureString"
  value_wo         = ephemeral.aws_secretsmanager_random_password.jwt_secret.random_password
  value_wo_version = local.jwt_secret_version
}

resource "aws_ssm_parameter" "encrypt_key" {
  name             = "${local.ssm_parameter_path}/TIKKLE_ENCRYPT_SECRET_KEY"
  description      = "Tikkle AES-256 key for stored Upbit keys (generated, 32 chars, write-only)"
  type             = "SecureString"
  value_wo         = ephemeral.aws_secretsmanager_random_password.encrypt_key.random_password
  value_wo_version = local.encrypt_key_version

  # 이 키를 잃으면 암호화된 업비트 키를 영영 풀 수 없다.
  lifecycle {
    prevent_destroy = true
  }
}
