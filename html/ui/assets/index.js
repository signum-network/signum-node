const nodeUrl = location.origin
const preferredAppKey = 'preferred-wallet'

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

function selectedApp(name) {
  if (document.getElementById('remember-wallet__checkbox').checked) {
    localStorage.setItem(preferredAppKey, name)
  }
}

(() => {
  getNodeVersion().then((version) => {
    document.getElementById('version').textContent = version
  })
  const appName = localStorage.getItem(preferredAppKey)
  if (appName) {
    document.getElementById(`${appName}-link`).click()
  }
})()

