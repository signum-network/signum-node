import type { Locale } from './en'

const hi: Locale = {
  nav: {
    dashboard: 'डैशबोर्ड',
    network: 'नेटवर्क',
    blocks: 'ब्लॉक',
    wallet: 'वॉलेट',
    soon: 'जल्द आ रहा है',
  },
  common: {
    refresh: '↻ ताज़ा करें',
    refreshing: 'ताज़ा हो रहा है…',
    copy: 'कॉपी करें',
    height: 'ऊँचाई',
    address: 'पता',
    failures: 'विफलताएँ',
    cachedAgo: '{{seconds}}स पहले कैश',
    noData: 'कोई डेटा नहीं',
    searchAgain: 'फिर से खोजें',
    startBinarySearch: 'बाइनरी खोज शुरू करें',
    searching: 'खोज रहे हैं… कुछ सेकंड लग सकते हैं',
    selectTheme: 'थीम चुनें',
    selectLanguage: 'भाषा चुनें',
  },
  status: {
    onChain: 'सिंक्रोनाइज़',
    stale: 'पुराना',
    forking: 'फोर्किंग',
    blacklisted: 'अवरुद्ध',
  },
  dashboard: {
    chainActivity: 'चेन गतिविधि',
    network: 'नेटवर्क',
    nodeHealth: 'नोड स्वास्थ्य',
  },
  network: {
    chainConsensus: 'चेन सहमति',
    peers: 'पीयर',
    forkHistory: 'फोर्क / पुनर्गठन इतिहास',
    blacklistRecommendations: 'अवरोध सुझाव',
    agree: '{{on}} सहमत · {{stale}} पुराने · {{forking}} फोर्किंग',
    onOurChain: 'हमारी चेन पर',
    noReorgs: 'नोड शुरू होने के बाद से कोई पुनर्गठन नहीं',
    noRecommendations: 'अभी कोई सुझाव नहीं',
    noPeerData: 'पीयर डेटा नहीं — स्थिति हर 120 सेकंड में अपडेट होती है',
    blacklistHint: 'node.properties में P2P.BlacklistedPeers में पते जोड़कर उन्हें अवरुद्ध करें।',
    peersOpen: '{{count}} पीयर ▲',
    peersOpen_other: '{{count}} पीयर ▲',
    peersClosed: '{{count}} पीयर ▼',
    peersClosed_other: '{{count}} पीयर ▼',
    forkPoint: {
      title: 'फोर्क पॉइंट खोज',
      findFork: 'फोर्क खोजें',
      findForkFull: 'फोर्क पॉइंट खोजें',
      forkHeight: 'फोर्क ऊँचाई',
      forkBlockId: 'फोर्क ब्लॉक ID',
      ourBlock: 'फोर्क पर हमारा ब्लॉक',
      steps: 'खोज चरण',
    },
    rollback: '-{{depth}} ब्लॉक',
    rollback_other: '-{{depth}} ब्लॉक',
  },
  info: {
    chainConsensus:
      'जुड़े हुए पीयर का वह प्रतिशत जिनकी संचयी कठिनाई हमारे साथ मेल खाती है या 5 ब्लॉक के भीतर है। ≥90% का मतलब है नेटवर्क कैनोनिकल चेन पर दृढ़ता से सहमत है।',
    cumulativeDifficulty:
      'उत्पत्ति के बाद से चेन में निवेश किया गया कुल proof-of-commitment कार्य। सबसे उच्च संचयी कठिनाई वाली चेन हमेशा कैनोनिकल चुनी जाती है — इतिहास को फिर से लिखना अत्यंत महंगा बनाता है।',
    findFork:
      'आपके नोड और चयनित पीयर के बीच बाइनरी खोज करके उस सटीक ब्लॉक ऊँचाई का पता लगाता है जहाँ चेनें अलग हुईं। फोर्क निदान के लिए उपयोगी।',
    networkCapacity:
      'वर्तमान बेस टारगेट से निकाले गए सभी सक्रिय खनिकों का अनुमानित कुल स्टोरेज प्रतिबद्धता (TiB/PiB)। अधिक क्षमता का अर्थ है अधिक नेटवर्क सुरक्षा।',
    baseTarget:
      'औसत ब्लॉक समय 4 मिनट रखने के लिए प्रत्येक ब्लॉक के बाद समायोजित होता है। कम मान का अर्थ है अधिक कठिनाई — माइनिंग पुरस्कार पाना कठिन।',
    peersTable:
      '"सिंक्रोनाइज़" पीयर हमारी समान संचयी कठिनाई साझा करते हैं। "पुराने" पीयर 5 ब्लॉक के भीतर हैं। "फोर्किंग" पीयर एक अलग चेन पर हैं।',
    blacklistRecommendations:
      'जो पीयर लगातार भिन्न चेन पर हैं या बार-बार कनेक्शन विफलता दिखाते हैं। उन्हें P2P.BlacklistedPeers में जोड़ने से नोड संसाधनों की बर्बादी रुकती है।',
  },
}

export default hi
