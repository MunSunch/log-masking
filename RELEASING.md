# Releasing to Maven Central

Артефакт `io.github.munsunch:log-masking-starter` публикуется в Maven Central через
[Central Portal](https://central.sonatype.com/) (сервис, заменивший OSSRH в 2024).
Релиз автоматизирован связкой **Gradle `maven-publish`** (сборка, POM, артефакты)
+ **[JReleaser](https://jreleaser.org/)** (GPG-подпись, загрузка и релиз в Central Portal).

---

## Разовая настройка

### 1. Регистрация namespace `io.github.munsunch` на Central Portal

Namespace `io.github.munsunch` относится к типу GitHub-namespace и верифицируется
**автоматически** по владению аккаунтом GitHub — DNS-записи и домен не нужны.

1. Залогиниться на <https://central.sonatype.com/> через **тот же** GitHub-аккаунт
   `MunSunch`, которому принадлежит репозиторий.
2. **View Namespaces → Add Namespace** → ввести `io.github.munsunch`.
3. Central Portal предложит подтвердить владение: создать в профиле GitHub
   публичный репозиторий со сгенерированным именем (что-то вроде
   `OSSRH-12345`) — Portal его увидит и пометит namespace как verified.
   Репозиторий после верификации можно удалить.

Смена groupId: если когда-нибудь нужно будет перейти на собственный домен
(например, `com.munsun`), это делается верификацией отдельного namespace через
DNS TXT и сменой `group` в `build.gradle.kts`. Координаты артефакта при этом
поменяются для потребителей.

### 2. Создание User Token

Central Portal → **View Account → Generate User Token** → получаем пару
`username`/`password` (это НЕ логин в портал, а отдельный токен).

### 3. Создание GPG-ключа и публикация публичной части

Central Portal требует, чтобы каждый артефакт был подписан ключом, опубликованным
на публичном keyserver.

```bash
# Генерация ключа (RSA 4096, без срока истечения, сильная passphrase)
gpg --full-generate-key

# Получить длинный ID ключа
gpg --list-secret-keys --keyid-format=long

# Опубликовать публичный ключ на keyservers, которые проверяет Central
gpg --keyserver keys.openpgp.org --send-keys <LONG_KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <LONG_KEY_ID>

# Экспортировать ключи для JReleaser (in-memory signing)
gpg --armor --export        <LONG_KEY_ID> > public.pgp
gpg --armor --export-secret-keys <LONG_KEY_ID> > private.pgp
```

Файлы `public.pgp` и `private.pgp` — секреты. Они уже попадают в `.gitignore`,
но в репозиторий их класть нельзя ни при каких условиях.

### 4. Прописать секреты в GitHub Actions

В `Settings → Secrets and variables → Actions → New repository secret` завести:

| Имя секрета | Содержимое |
|---|---|
| `JRELEASER_GPG_PASSPHRASE` | Пароль от GPG-ключа |
| `JRELEASER_GPG_PUBLIC_KEY` | Содержимое `public.pgp` целиком |
| `JRELEASER_GPG_SECRET_KEY` | Содержимое `private.pgp` целиком |
| `JRELEASER_MAVENCENTRAL_USERNAME` | Username из User Token |
| `JRELEASER_MAVENCENTRAL_TOKEN` | Password из User Token |

`GITHUB_TOKEN` GitHub Actions предоставляет автоматически — отдельно заводить не нужно.

---

## Чеклист релиза

1. Убедиться, что `main` собирается зелёным: `./gradlew build`.
2. Определить номер релиза (semver, **без `-SNAPSHOT`**), например `0.1.0`.
3. Локальная проверка публикации (в `~/.m2/repository`):
   ```bash
   ./gradlew :log-masking-starter:publishToMavenLocal -PreleaseVersion=0.1.0
   ls ~/.m2/repository/io/github/munsunch/log-masking-starter/0.1.0/
   ```
   Ожидается: `log-masking-starter-0.1.0.jar`, `-sources.jar`, `-javadoc.jar`,
   `.pom`, `.module` + чексуммы.
4. Локальный dry-run JReleaser (без реальной загрузки) — требует CLI
   `jreleaser` (install: `brew install jreleaser` на macOS,
   [standalone binary](https://github.com/jreleaser/jreleaser/releases) на Windows,
   или Docker: `docker run --rm -v "$PWD":/workspace jreleaser/jreleaser-slim config`):
   ```bash
   export JRELEASER_PROJECT_VERSION=0.1.0
   export JRELEASER_GPG_PASSPHRASE=...
   export JRELEASER_GPG_PUBLIC_KEY="$(cat public.pgp)"
   export JRELEASER_GPG_SECRET_KEY="$(cat private.pgp)"
   export JRELEASER_MAVENCENTRAL_USERNAME=...
   export JRELEASER_MAVENCENTRAL_TOKEN=...
   export JRELEASER_GITHUB_TOKEN=...

   ./gradlew :log-masking-starter:publish -PreleaseVersion=0.1.0
   jreleaser config   # проверить конфиг без загрузки
   ```
   Можно пропустить и положиться на CI — push тега уже делает полный прогон.
5. Запушить git-тег вида `vX.Y.Z`:
   ```bash
   git tag -a v0.1.0 -m "Release 0.1.0"
   git push origin v0.1.0
   ```
   Workflow `.github/workflows/release.yml` подхватит тег, соберёт артефакты,
   подпишет их и загрузит в Central Portal.
6. Зайти на <https://central.sonatype.com/publishing/deployments>, дождаться
   валидации и нажать **Publish** (ручная публикация включена флагом
   `automaticRelease = false` — можно переключить позже, когда пайплайн устоится).
7. Через ~30 минут артефакт появится на
   `https://repo1.maven.org/maven2/io/github/munsunch/log-masking-starter/0.1.0/`.

---

## Локальные команды

```bash
# Собрать всё и прогнать тесты
./gradlew build

# Стейдж артефактов для Central Portal (→ log-masking-starter/build/staging-deploy/
# на Linux/macOS, C:/tmp/log-masking-build/log-masking-starter/staging-deploy/ на Windows)
./gradlew :log-masking-starter:publish -PreleaseVersion=0.1.0

# Проверить конфигурацию JReleaser (требует CLI и env vars, см. пункт 4 чеклиста)
jreleaser config

# Полный релиз: подпись + загрузка + публикация в Central Portal (делает CI из тега)
jreleaser full-release
```

Параметр `-PreleaseVersion=X.Y.Z` перезаписывает дефолтное `0.1.0-SNAPSHOT` на
время релиза без правок `build.gradle.kts`. В ежедневной разработке версия
остаётся SNAPSHOT-ом.

Конфиг JReleaser лежит в `jreleaser.yml` в корне репо. Gradle-плагин
`org.jreleaser` был убран из-за конфликта `commons-compress` на classpath
Gradle — вместо него используется standalone CLI.

---

## Траблшутинг

**`Deployer mavenCentral:sonatype is not enabled. Skipping`** при `jreleaser config`.
JReleaser пропускает деплой для `-SNAPSHOT`-версий (Central Portal принимает
релизы только на release-URL). Установите `JRELEASER_PROJECT_VERSION=0.1.0`.

**`signing.pgp.secretKey не может быть пустым`.** Переменные `JRELEASER_GPG_*`
не попали в процесс. В shell делайте `export`, а не просто присваивание;
в GitHub Actions проверьте, что секрет заведён и `env:`-блок его передаёт.

**`401 Unauthorized` при загрузке.** Токен `JRELEASER_MAVENCENTRAL_TOKEN`
привязан к аккаунту, на котором сгенерирован. Убедитесь, что `io.github.munsunch`
помечен как **verified** на том же аккаунте Central Portal и что токен не
протух (User Token можно перегенерировать).

**Javadoc упал на доклинте.** В `log-masking-starter/build.gradle.kts`
`Xdoclint:none` уже выключает строгие проверки. Если всё равно падает —
это настоящая ошибка в javadoc-комментарии (например, битый `{@link}`).

**Публикация SNAPSHOT-ов.** Central Portal принимает snapshots на отдельном
endpoint `https://central.sonatype.com/repository/maven-snapshots/`. Для этого
нужен отдельный deployer типа `nexus2`; в текущей конфигурации его нет.
Добавьте, если понадобится CI-пайплайн на snapshot-ы.
