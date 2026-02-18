-- Add OAuth provider columns to user table (IF NOT EXISTS for idempotency)
ALTER TABLE `user`
    ADD COLUMN IF NOT EXISTS `oauthProvider`     varchar(50)  NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `oauthProviderId`   varchar(255) NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `oauthProviderData` text         NULL DEFAULT NULL;

-- Index for looking up users by OAuth provider
CREATE INDEX IF NOT EXISTS `idx_user_oauth` ON `user` (`oauthProvider`, `oauthProviderId`);
