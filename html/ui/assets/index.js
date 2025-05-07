const nodeUrl = location.origin
const preferredWalletKey = 'preferred-wallet'

async function getNodeVersion() {
  const url = `${nodeUrl}/api?requestType=getBlockchainStatus`
  const res = await fetch(url)

  if (!res.ok) {
    console.error('Request to peer failed', res.status)
    return ''
  }

  const {version} = await res.json()
  return version
}

function selectedWallet(name) {
  if (document.getElementById('remember-wallet__checkbox').checked) {
    localStorage.setItem(preferredWalletKey, name)
  }
}

(() => {
  getNodeVersion().then((version) => {
    document.getElementById('version').textContent = version
  })
  const walletName = localStorage.getItem(preferredWalletKey)
  if (walletName) {
    document.getElementById(`${walletName}-link`).click()
  }
})()

