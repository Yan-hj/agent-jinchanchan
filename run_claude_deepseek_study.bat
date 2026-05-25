@echo off

set "ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic"
set "ANTHROPIC_AUTH_TOKEN=sk-131eaefbc97b4e67beeed9540f56bcb7"

set "ANTHROPIC_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_OPUS_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_SONNET_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_HAIKU_MODEL=deepseek-v4-flash"
set "CLAUDE_CODE_SUBAGENT_MODEL=deepseek-v4-flash"
set "CLAUDE_CODE_EFFORT_LEVEL=max"

claude
pause
