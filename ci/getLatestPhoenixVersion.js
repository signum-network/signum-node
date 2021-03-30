/**
 * This script fetches the latest download url for phoenix web wallet
 *
 * As it uses async/await at least NodeJS 12 is needed
 *
 * @author ohager, Burstcoin, 2021
 */
const http = require('https')

const fetchGithubReleases = () => {
    return new Promise((resolve, reject) => {
        http.get('https://api.github.com/repos/burst-apps-team/phoenix/releases?per_page=25&page=0', {
            headers: {
                Accept: 'application/vnd.github.v3+json',
                'User-Agent': 'burstcoin',
            },
        }, (res) => {
            const {statusCode} = res;
            const contentType = res.headers['content-type'];

            let error;
            if (statusCode - 200 > 100) {
                error = new Error('Request Failed.\n' +
                    `Status Code: ${statusCode}`);
            } else if (!/^application\/json/.test(contentType)) {
                error = new Error('Invalid content-type.\n' +
                    `Expected application/json but received ${contentType}`);
            }
            if (error) {
                // Consume response data to free up memory
                res.resume();
                reject(error)
                return;
            }

            res.setEncoding('utf8');
            let rawData = '';
            res.on('data', (chunk) => {
                rawData += chunk;
            });
            res.on('end', () => {
                try {
                    const parsedData = JSON.parse(rawData);
                    resolve(parsedData)
                } catch (e) {
                    reject(e.message)
                }
            });
        }).on('error', reject);
    })

}

function getCurrentPhoenixWebWalletDownloadUrl(releases) {
    const desktopReleases = releases
        .filter(({tag_name, draft, prerelease}) => !draft && !prerelease && tag_name.startsWith('desktop-'))
    if (!desktopReleases.length) {
        throw "WTF? No releases found!?"
    }

    // releases are ordered by date already
    const {
        name,
        browser_download_url: downloadUrl
    } = desktopReleases[0].assets.find(({name}) => name.startsWith('web-phoenix-burst-wallet'))
    const version = name.replace('web-phoenix-burst-wallet.', '').replace('.zip', '')
    return {
        version,
        downloadUrl
    }
}

(async () => {
    try {
        const releases = await fetchGithubReleases()
        const {version, downloadUrl} = getCurrentPhoenixWebWalletDownloadUrl(releases)
        console.log(`version: ${version},url: ${downloadUrl}`)
    } catch (e) {
        console.error(e)
    }
})()
