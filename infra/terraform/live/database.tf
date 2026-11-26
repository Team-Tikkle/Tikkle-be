# 원장 DB. MySQL 8.4, 서버 1대 구성에 맞춘 Single-AZ.
# 스키마는 앱의 Flyway가 만든다(빈 DB로 시작).

resource "aws_db_subnet_group" "main" {
  name        = "tikkle-db"
  description = "Tikkle RDS - private subnets in 2a and 2c"
  subnet_ids  = [for s in aws_subnet.private : s.id]

  tags = {
    Name = "tikkle-db"
  }
}

# 기본 파라미터 그룹은 시간대가 UTC이고 TLS를 강제하지 않는다.
resource "aws_db_parameter_group" "mysql84" {
  name        = "tikkle-mysql84"
  family      = "mysql8.4"
  description = "Tikkle MySQL 8.4 - KST time zone, TLS required"

  parameter {
    name         = "time_zone"
    value        = "Asia/Seoul"
    apply_method = "immediate"
  }

  # 앱의 DB_URL은 sslMode=REQUIRED 로 붙는다. DB 쪽에서도 평문 접속을 막는다.
  parameter {
    name         = "require_secure_transport"
    value        = "1"
    apply_method = "immediate"
  }
}

# 마스터 비밀번호. 적용할 때마다 AWS가 새 난수를 만들지만, 실제로 쓰이는 것은
# 아래 버전 번호가 바뀐 적용뿐이다. 값은 Terraform 상태에 남지 않는다(write-only).
# 특수문자를 빼는 것은 .env·JDBC URL에서 & $ 같은 문자가 값을 깨뜨린 적이 있어서다.
ephemeral "aws_secretsmanager_random_password" "db_master" {
  password_length     = 32
  exclude_punctuation = true
  include_space       = false
}

locals {
  # 비밀번호를 바꿀 때 1씩 올린다. RDS와 SSM(/tikkle/prod/DB_PASSWORD)이 함께 바뀐다.
  # 적용이 중간에 실패해 한쪽만 바뀌었으면 이 값을 올려 다시 적용하면 맞춰진다.
  db_password_version = 1
}

resource "aws_db_instance" "main" {
  identifier     = "tikkle-db"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = "db.t4g.micro"

  # 표준 지원이 끝나도 유료 Extended Support에 자동 등록되지 않게 한다.
  engine_lifecycle_support = "open-source-rds-extended-support-disabled"

  db_name             = "tikkle_db"
  username            = "tikkle"
  password_wo         = ephemeral.aws_secretsmanager_random_password.db_master.random_password
  password_wo_version = local.db_password_version

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.mysql84.name
  availability_zone      = "ap-northeast-2a" # 서버와 같은 AZ
  multi_az               = false
  publicly_accessible    = false
  port                   = 3306

  # 시각은 UTC로 적는다. 백업 03:00–03:30 KST, 점검 월 05:00–05:30 KST.
  # 스케줄러(02·04·07·14·18시 KST)와 겹치지 않게 했다.
  backup_retention_period = 7
  backup_window           = "18:00-18:30"
  maintenance_window      = "sun:20:00-sun:20:30"

  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false
  apply_immediately           = false

  performance_insights_enabled = false
  monitoring_interval          = 0

  copy_tags_to_snapshot     = true
  delete_automated_backups  = false
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "tikkle-db-final"

  tags = {
    Name = "tikkle-db"
  }

  # 실돈 원장이다. AWS의 삭제 방지와 Terraform의 삭제 금지를 함께 둔다.
  lifecycle {
    prevent_destroy = true
  }
}
