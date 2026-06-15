package com.tikkle.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ClassificationRequestEvent extends ApplicationEvent {
    private final Long paymentEventId;
    private final String merchant;

    public ClassificationRequestEvent(Object source, Long paymentEventId, String merchant) {
        super(source);
        this.paymentEventId = paymentEventId;
        this.merchant = merchant;
    }
}