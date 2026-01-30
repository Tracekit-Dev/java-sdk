# Maven Central Deployment Guide

This guide walks you through publishing the TraceKit Java SDK to Maven Central via Sonatype OSSRH.

## Prerequisites

1. **Sonatype OSSRH Account**: Signed up at https://central.sonatype.com/ (GitHub login supported)
2. **GPG Key**: For signing artifacts
3. **Maven Settings**: Configured with Sonatype credentials

## Important: New Publishing Process (2024+)

**Note**: The old `issues.sonatype.org` JIRA system has been decommissioned. All namespace requests are now handled through the Central Portal web interface at https://central.sonatype.com/

## Step 1: Request Namespace Access

1. Go to https://central.sonatype.com/
2. Log in with your GitHub account (already done ✓)
3. Navigate to **"Namespaces"** → Click **"Add Namespace"**
4. Enter `dev.tracekit` as the namespace
5. Verify ownership by either:
   - **DNS TXT Record** (Recommended): Add a TXT record to `tracekit.dev`:
     ```
     Name: @
     Type: TXT
     Value: [verification code provided by Sonatype in the portal]
     ```
   - **GitHub Repository**: Create a public repository in the `Tracekit-Dev` organization with the name provided by the portal

6. Submit the verification through the portal
7. Wait for automatic approval (usually instant for DNS, or within hours for GitHub verification)

**No support ticket needed** - the portal handles everything automatically!

## Step 2: Generate GPG Key

GPG is already installed. Now generate a signing key:

```bash
# Generate GPG key
gpg --gen-key

# Use these details:
# Name: TraceKit Dev
# Email: dev@tracekit.dev (or your email)
# Passphrase: (choose a strong passphrase and save it!)
```

After generating, list your keys:

```bash
gpg --list-secret-keys --keyid-format=long
```

Output will look like:
```
sec   rsa3072/YOUR_KEY_ID 2024-01-30 [SC]
      ABCDEF1234567890ABCDEF1234567890ABCDEF12
uid                 [ultimate] TraceKit Dev <dev@tracekit.dev>
ssb   rsa3072/SUBKEY_ID 2024-01-30 [E]
```

Copy `YOUR_KEY_ID` (the part after `rsa3072/`).

## Step 3: Publish GPG Key to Keyserver

```bash
# Publish to Ubuntu keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Also publish to other common keyservers (optional but recommended)
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID
```

## Step 4: Generate User Token

1. Go to https://central.sonatype.com/account
2. Click **"Generate User Token"** (or similar button in the API/Token section)
3. The portal will display:
   - **Username**: A short token (e.g., `aBC1dEf2`)
   - **Password**: A longer token (e.g., `ghIJ3klM4nOPq5rsT6uvW7xyZ8`)
4. **Copy both values immediately** - you won't be able to see them again!

## Step 5: Configure Maven Settings

Edit or create `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>TOKEN_USERNAME_FROM_STEP_4</username>
      <password>TOKEN_PASSWORD_FROM_STEP_4</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

Replace:
- `TOKEN_USERNAME_FROM_STEP_4`: The username token from the Central Portal
- `TOKEN_PASSWORD_FROM_STEP_4`: The password token from the Central Portal
- `YOUR_GPG_PASSPHRASE`: The passphrase you set when generating the GPG key

**Important**: Use the generated token credentials, NOT your actual Sonatype account username/password!

## Step 6: Update Version Number

Before deploying, update the version in `pom.xml`:

```xml
<!-- Change from SNAPSHOT to release version -->
<version>1.0.0</version>
```

**Important**: Maven Central does not accept SNAPSHOT versions for releases.

## Step 7: Deploy to Maven Central

Once your namespace is approved and settings are configured:

```bash
# Clean build and deploy
mvn clean deploy

# This will:
# 1. Build all modules
# 2. Run tests
# 3. Generate source JARs
# 4. Generate Javadoc JARs
# 5. Sign all artifacts with GPG
# 6. Upload to Sonatype OSSRH staging repository
# 7. Automatically release to Maven Central (autoReleaseAfterClose=true)
```

## Step 8: Verify Deployment

1. Check your publishing dashboard at https://central.sonatype.com/publishing
2. Monitor the deployment status (it will show validation, publishing, and sync status)
3. Wait 10-30 minutes for the release to sync to Maven Central
4. Verify at https://search.maven.org/search?q=g:dev.tracekit

The new portal provides real-time status updates, unlike the old OSSRH system.

## Troubleshooting

### GPG Signing Fails

```bash
# Make sure GPG agent is running
gpgconf --kill gpg-agent
gpgconf --launch gpg-agent

# Test signing
echo "test" | gpg --clearsign
```

### Authentication Fails

- Generate a new token at https://central.sonatype.com/account
- Use the token as your password in `settings.xml`, not your account password

### Namespace Not Approved

- Check the status at https://central.sonatype.com/publishing/namespaces
- Ensure DNS verification is complete (may take up to 24 hours to propagate)
- If you need help, email Central Support at central-support@sonatype.com (no JIRA tickets anymore!)

## Deploying Snapshot Versions

For SNAPSHOT versions (e.g., `1.0.0-SNAPSHOT`):

```bash
# Version must end with -SNAPSHOT
mvn clean deploy
```

Snapshots are deployed to:
- https://s01.oss.sonatype.org/content/repositories/snapshots/

And can be used by adding to `pom.xml`:

```xml
<repositories>
  <repository>
    <id>ossrh-snapshots</id>
    <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

## Post-Release Checklist

After successful deployment:

1. Update README.md to remove `-SNAPSHOT` from installation instructions
2. Create a GitHub release with tag matching the version (e.g., `v1.0.0`)
3. Announce the release to users
4. Bump version to next SNAPSHOT (e.g., `1.1.0-SNAPSHOT`) for continued development

## Reference Links

- Sonatype Central Portal: https://central.sonatype.com/
- Maven Central Search: https://search.maven.org/
- OSSRH Guide: https://central.sonatype.org/publish/publish-guide/
- GPG Guide: https://central.sonatype.org/publish/requirements/gpg/
