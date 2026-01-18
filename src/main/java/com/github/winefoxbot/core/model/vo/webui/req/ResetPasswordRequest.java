package com.github.winefoxbot.core.model.vo.webui.req;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:26
 */
public record ResetPasswordRequest(
    String recoverToken,
    String newPassword
) {
}
