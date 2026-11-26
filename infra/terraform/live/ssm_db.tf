# 앱의 DB 접속 정보. 배포 스크립트가 이 이름 그대로 읽어 .env 를 만든다.
# 나머지 앱 비밀값(JWT·AES·HMAC·외부 키)은 사람이 넣는다(4-5).

locals {
  db_url = join("", [
    "jdbc:mysql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}",
    "?sslMode=REQUIRED",
    "&connectionTimeZone=Asia/Seoul",
    "&forceConnectionTimeZoneToSession=true",
    "&characterEncoding=UTF-8",
  ])
}

resource "aws_ssm_parameter" "db_url" {
  name        = "${local.ssm_parameter_path}/DB_URL"
  description = "Tikkle DB JDBC URL"
  type        = "SecureString"
  value       = local.db_url
}

resource "aws_ssm_parameter" "db_username" {
  name        = "${local.ssm_parameter_path}/DB_USERNAME"
  description = "Tikkle DB user"
  type        = "SecureString"
  value       = aws_db_instance.main.username
}

# RDS에 넣은 것과 같은 난수. 상태에 남지 않는다(write-only).
resource "aws_ssm_parameter" "db_password" {
  name             = "${local.ssm_parameter_path}/DB_PASSWORD"
  description      = "Tikkle DB password (generated, write-only)"
  type             = "SecureString"
  value_wo         = ephemeral.aws_secretsmanager_random_password.db_master.random_password
  value_wo_version = local.db_password_version
}
