# 값은 live/terraform.tfvars 에 둔다 (gitignore). 틀은 terraform.tfvars.example.

variable "budget_limit_usd" {
  description = "월 예산(USD). 크레딧을 빼기 전 사용액 기준. 기본값은 크레딧 $1,000을 12개월로 나눈 값"
  type        = number
  default     = 85
}

variable "budget_emails" {
  description = "예산 알림을 받을 이메일 주소. 레포에는 적지 않는다"
  type        = list(string)

  validation {
    condition     = length(var.budget_emails) > 0
    error_message = "예산 알림을 받을 이메일을 하나 이상 넣어야 한다."
  }
}
