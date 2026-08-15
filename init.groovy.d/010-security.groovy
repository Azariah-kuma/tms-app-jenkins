// 初回起動時のみ実行: 管理ユーザーを作成し、匿名アクセスを禁止する。
// ユーザー名/パスワードは環境変数 JENKINS_ADMIN_USER / JENKINS_ADMIN_PASSWORD から読む
// (イメージやリポジトリに平文で残さないため)。
import jenkins.model.*
import hudson.security.*

def instance = Jenkins.get()

if (!(instance.getSecurityRealm() instanceof HudsonPrivateSecurityRealm)) {
    def env = System.getenv()
    def user = env['JENKINS_ADMIN_USER'] ?: 'admin'
    def password = env['JENKINS_ADMIN_PASSWORD']

    if (!password) {
        throw new IllegalStateException('JENKINS_ADMIN_PASSWORD が設定されていません')
    }

    def realm = new HudsonPrivateSecurityRealm(false)
    realm.createAccount(user, password)
    instance.setSecurityRealm(realm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(strategy)

    instance.save()
}