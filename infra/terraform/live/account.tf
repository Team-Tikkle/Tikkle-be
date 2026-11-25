# 계정(서울 리전) 단위 기본값.

# 새로 만드는 EBS 볼륨을 모두 암호화한다(기본 키 aws/ebs). 서버 디스크(4-6)도 여기에 걸린다.
resource "aws_ebs_encryption_by_default" "this" {
  enabled = true
}

# 계정의 어떤 S3 버킷도 공개되지 않게 막는다. 랜딩(Amplify)은 Git 연결 앱이라 계정 버킷을 쓰지 않는다.
resource "aws_s3_account_public_access_block" "this" {
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
