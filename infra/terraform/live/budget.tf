# 월 비용 예산. 콘솔에서 만든 예산을 가져와(imports.tf) 이름은 그대로 둔다.
# 기준은 크레딧·환불을 빼기 전 사용액(UnblendedCost)이다 — 크레딧이 얼마나 빨리 줄어드는지 보려고.
# 알림: 실제 50%·80%·100%, 예측 100%.

locals {
  budget_actual_thresholds = [50, 80, 100]
}

resource "aws_budgets_budget" "monthly" {
  name        = "My Monthly Cost Budget"
  budget_type = "COST"
  # AWS가 "85.0" 꼴로 돌려주므로 같은 꼴로 적는다(되읽을 때 차이가 나지 않게).
  limit_amount     = format("%.1f", var.budget_limit_usd)
  limit_unit       = "USD"
  time_unit        = "MONTHLY"
  metrics          = ["UnblendedCost"]
  billing_view_arn = "arn:aws:billing::${local.account_id}:billingview/primary"

  filter_expression {
    not {
      dimensions {
        key    = "RECORD_TYPE"
        values = ["Credit", "Refund"]
      }
    }
  }

  dynamic "notification" {
    for_each = local.budget_actual_thresholds

    content {
      notification_type          = "ACTUAL"
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      subscriber_email_addresses = var.budget_emails
    }
  }

  notification {
    notification_type          = "FORECASTED"
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    subscriber_email_addresses = var.budget_emails
  }
}
