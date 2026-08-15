# tms-app 用 Jenkins サーバー

tms-app プロジェクト(Laravel 13 + Angular)の `Jenkinsfile` を実行するための、
アプリ本体のDockerスタック(compose.yaml / Sail)とは独立したJenkinsサーバー。

## 構成概要

- ベースイメージ: `jenkins/jenkins:lts` に Docker CLI (Docker Compose v2)・Node.js 22 を追加。
- DooD (Docker outside of Docker) 構成: ホストの `/var/run/docker.sock` をマウントし、
  Jenkinsコンテナの中からホストのDockerデーモンを操作する。
- `docker compose` のbind mount (`.:/var/www/html`) がホストパス基準で解決されるため、
  プロジェクトの親ディレクトリ `/home/tabata/projects` をJenkinsコンテナにも
  **ホストと同一の絶対パス**でマウントしている(子の `tms-app` だけをマウントすると、
  親ディレクトリがコンテナ内でroot所有の自動生成ディレクトリになり、Jenkinsの
  一時ディレクトリ作成で `Permission denied` になるため、親ごとマウントが必要だった)。
- 管理ユーザー作成・Pipelineジョブ作成は `init.groovy.d/` のスクリプトで初回起動時に自動実行。
  セットアップウィザードは無効化している。

## 既存の開発スタックとの分離について(重要)

`compose.yaml` の Compose project 名はディレクトリ名から自動的に `tms-app` になり、
開発中の既存コンテナ(`tms-app-pgsql-1` 等)と同じ名前空間になる。
Jenkinsfile の `post.always` は `docker compose down -v` を実行するため、
そのまま実行すると **開発中の全コンテナ・ボリュームを巻き込んで削除してしまう**。

Compose projectとDBポートの分離自体は `Jenkinsfile` の `environment` ブロックで
既に自己完結している(`COMPOSE_PROJECT_NAME = 'tms-app-ci'`, `FORWARD_DB_PORT = '5433'`)。

`init.groovy.d/020-create-job.groovy` はジョブ作成時に、これとは別に必要な
workspace固定のみを注入する
(リポジトリ本体のJenkinsfileは変更せず、Jenkins内部のインライン複製のみ)。

- `agent any` → `customWorkspace '/home/tabata/projects/tms-app'` を使うagentに置換
  (ジョブのworkspaceをホスト上の実パスに固定するため)

以前は `environment` ブロックにも同じ変数を注入していたが、Jenkinsfile側に
既に直書きされたため重複定義エラーとなり廃止した。

## ビルド・起動

```bash
# credsStore エラー回避用の空docker config (このマシン固有の問題)
mkdir -p ~/.docker-empty-config && echo '{"auths":{}}' > ~/.docker-empty-config/config.json

cd /home/tabata/projects/tms-app-jenkins
DOCKER_CONFIG=~/.docker-empty-config docker build \
  --build-arg DOCKER_HOST_GID=$(stat -c '%g' /var/run/docker.sock) \
  -t tms-app-jenkins:lts .

docker volume create tms-app-jenkins-home

DOCKER_CONFIG=~/.docker-empty-config docker run -d \
  --name tms-app-jenkins \
  -p 8080:8080 -p 50000:50000 \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -v tms-app-jenkins-home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /home/tabata/projects:/home/tabata/projects \
  -e JENKINS_ADMIN_USER=admin \
  -e JENKINS_ADMIN_PASSWORD='<パスワード>' \
  tms-app-jenkins:lts
```

## アクセス

- URL: http://localhost:8080
- 管理ユーザー: `admin` / パスワードは起動時に `JENKINS_ADMIN_PASSWORD` として渡したもの
  (`.admin-password` に保存。取り扱い注意、gitignore推奨)
- ジョブ名: `tms-app-pipeline`
