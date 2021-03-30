const nodeUrl = location.origin
const preferredWalletKey = 'preferred-wallet'

async function getBRSVersion() {
    const url = `${nodeUrl}/burst?requestType=getState`
    const res = await fetch(url)

    if (!res.ok) {
        console.error('Request to peer failed', res.status)
        return ''
    }

    const {version} = await res.json()
    return version
}

function selectedWallet(name){
    if(document.getElementById('remember-wallet__checkbox').checked){
        localStorage.setItem(preferredWalletKey,name)
    }
}

(async () => {
    const version = await getBRSVersion()
    const walletName = localStorage.getItem(preferredWalletKey)
    document.getElementById(`${walletName}-link`).click()
    document.getElementById('version').textContent = version
})()
