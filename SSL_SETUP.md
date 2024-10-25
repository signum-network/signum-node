# SSL Configuration

## Signum Node - Local SSL Configuration

This guide explains how to generate SSL certificates to run a Signum Node locally with HTTPS enabled.

### Prerequisites

Ensure you have `openssl` installed on your system. You can verify this by running the following command:

```bash
openssl version
```

If not installed, you can install it using your package manager (e.g., `brew install openssl` on macOS,
`sudo apt install openssl` on Ubuntu).

### Steps to Generate SSL Certificates

1. **Generate a private key**

   Use the following command to generate a private RSA key:

   ```bash
   openssl genpkey -algorithm RSA -out localhost.pem
   ```

2. **Generate a self-signed certificate**

   With the private key, create a self-signed certificate valid for 365 days:

   ```bash
   openssl req -x509 -new -key localhost.pem -out localhost_chain.pem -days 365
   ```

   You will be prompted to fill in some details like Country, State, and Common Name. For local development, you can use
   `localhost` as the Common Name (CN).

3. **Generate a keystore**

   Finally, create a PKCS#12 keystore that bundles the private key and certificate together:

   ```bash
   openssl pkcs12 -export -inkey localhost.pem -in localhost_chain.pem -out localhost_keystore.p12 -name "localhost" -password pass:development
   ```

   This creates a keystore named `localhost_keystore.p12` protected with the password `development`.

### Update `node.properties`

In your `node.properties` file, enable SSL for the API and point to the newly created keystore. Add or update the
following lines:

```properties
API.SSL=on
API.SSL_keyStorePath=./localhost_keystore.p12
API.SSL_keyStorePassword=development
```

### Final Steps

1. Restart the Signum Node to apply the changes.
2. Your Signum Node should now be running locally with SSL enabled.

You can access it using `https://localhost:<your_port>` and/or `wss://localhost:<your_port>/events` with the port number
configured for your node.

## Using Certbot to Generate SSL Certificates for a Signum Node for your custom domain

Certbot is a tool used to automate the process of obtaining and renewing SSL certificates from Let's Encrypt or other
Certificate Authorities. This guide explains how to use Certbot to generate SSL certificates for running a Signum Node
locally.

### Prerequisites

1. **Certbot installation**: Ensure Certbot is installed. You can check by running:

   ```bash
   certbot --version
   ```

   If it's not installed, follow the [official installation guide](https://certbot.eff.org/instructions) for your
   system.

2. **Domain name**: To use Certbot, you need a publicly accessible domain (Certbot won't work for pure localhost
   setups). If you are running a local node accessible from the internet (e.g., via a reverse proxy like Nginx), you'll
   need a registered domain name pointing to your local machine.

3. **Port forwarding (optional)**: If your node is not publicly accessible, you may need to set up port forwarding to
   allow Certbot to perform HTTP-01 or DNS-01 validation.

### Request a certificate

Run Certbot to obtain a certificate for your domain. Replace `yourdomain.com` with your actual domain name.

```bash
sudo certbot certonly --standalone -d yourdomain.com
```

Certbot will generate the necessary files, including the certificate (`.crt`) and private key (`.key`).

By default, these will be stored in `/etc/letsencrypt/live/yourdomain.com/`.

> The Signum Node looks into the "letsencryptpath" and converts it to the necesary keystore file. No further action necessary here.

### Update `node.properties`

In your `node.properties` file, enable SSL for the API and configure the path to the Certbot-generated keystore:

```properties
API.SSL=on
# the file name of your keystore file. Let's Encrypt Cert will be automatically converted and stored under this path.
API.SSL_keyStorePath=./keystore.p12
API.SSL_keyStorePassword=<your_password>
# your path of letsencrypt certs. The Node looks for "privkey.pem" and "fullchain.pem" files
API.SSL_letsencryptPath=/etc/letsencrypt/live/<yourdomain>.com
```

### Automating Certificate Renewal

Certbot certificates expire every 90 days. You can automate the renewal process using Certbot's cron job feature.

> Signum Nodes reloads the certificate on startup and/or every 7 days while running

1. Set up a cron job to automatically renew certificates:

   ```bash
   sudo crontab -e
   ```

2. Add the following line to renew certificates automatically:

   ```bash
   0 0 * * * certbot renew --quiet
   ```

### Final Steps

1. Restart your Signum Node after the certificate is created and the `node.properties` file is updated.
2. Access the Signum Node using `https://yourdomain.com:<your_port>`.
