package com.tikkle.settings.entity;

import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean pushEnabled;

    @Builder
    private NotificationSettings(User user, boolean pushEnabled) {
        this.user = user;
        this.pushEnabled = pushEnabled;
    }

    public void changePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }
}
