@echo off
set "PATH=C:\Users\HP\.local\bin;%PATH%"

set "ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic"
set "ANTHROPIC_AUTH_TOKEN="sk-9ffe868b5cfd4e35aa42ade8351db064"

set "ANTHROPIC_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_OPUS_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_SONNET_MODEL=deepseek-v4-pro[1m]"
set "ANTHROPIC_DEFAULT_HAIKU_MODEL=deepseek-v4-flash"
set "CLAUDE_CODE_SUBAGENT_MODEL=deepseek-v4-flash"
set "CLAUDE_CODE_EFFORT_LEVEL=max"

claude
pause