// 初回起動時のみ実行: tms-app リポジトリの Jenkinsfile を元に Pipeline ジョブを作成する。
//
// リポジトリの Jenkinsfile 自体は書き換えない。
// このJenkinsコンテナはDooD構成(ホストのdocker.sockを共有)のため、
// docker compose のbind mount(`.:/var/www/html`)がホストから見て正しいパスになるよう、
// ジョブのworkspaceをホスト上のプロジェクトパスと同一の絶対パスに固定する必要がある。
// なお、開発中に既に動いている docker compose スタックとの分離(CI専用のCompose
// project名・DBポート)は Jenkinsfile 側の environment ブロックで既に自己完結して
// いるため、ここでは注入しない(かつてはここで注入していたが、Jenkinsfile側に
// 同じ変数が直書きされたことで重複定義エラーになったため廃止した)。
import jenkins.model.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def instance = Jenkins.get()
def jobName = 'tms-app-pipeline'
def projectPath = '/home/tabata/projects/tms-app'
def jenkinsfilePath = "${projectPath}/Jenkinsfile"

if (instance.getItem(jobName) == null) {
    def original = new File(jenkinsfilePath).text

    def customized = original
        .replace(
            '    agent any\n',
            "    agent {\n" +
            "        node {\n" +
            "            label 'built-in'\n" +
            "            customWorkspace '${projectPath}'\n" +
            "        }\n" +
            "    }\n"
        )

    if (customized == original) {
        throw new IllegalStateException('Jenkinsfile の agent 置換に失敗しました。フォーマットが変わっていないか確認してください。')
    }

    def job = instance.createProject(WorkflowJob.class, jobName)
    job.setDefinition(new CpsFlowDefinition(customized, true))
    job.save()
}

instance.save()