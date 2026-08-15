# tms-app 用 Jenkinsサーバー
#
# ベースは公式 jenkins/jenkins:lts。以下を追加している。
#   - Docker CLI + Docker Compose v2 (DooD: ホストの docker.sock をマウントして使う)
#   - Node.js (frontend/ の npm ci / ng test 用。Angular 22 は Node 20+ が必要)
#   - 必要プラグイン (Pipeline, JUnit, Coverage) の事前インストール
#   - 初期セットアップ(管理ユーザー作成・ジョブ作成)を行う init.groovy.d スクリプト
FROM jenkins/jenkins:lts
USER root

# Docker CLI + Compose v2 プラグイン
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# Node.js (frontend の npm ci / ng test 用)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# ホストの /var/run/docker.sock (gid はビルド時に --build-arg で渡す) に
# jenkinsユーザーからアクセスできるよう、同じgidの補助グループを作成して所属させる。
ARG DOCKER_HOST_GID=1001
RUN existing_group="$(getent group ${DOCKER_HOST_GID} | cut -d: -f1)"; \
    if [ -n "$existing_group" ]; then \
        usermod -aG "$existing_group" jenkins; \
    else \
        groupadd -g ${DOCKER_HOST_GID} docker-host && usermod -aG docker-host jenkins; \
    fi

# 初回セットアップウィザードは使わず、init.groovy.d で管理ユーザー作成とジョブ作成を行う
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
COPY init.groovy.d/*.groovy /usr/share/jenkins/ref/init.groovy.d/

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER jenkins