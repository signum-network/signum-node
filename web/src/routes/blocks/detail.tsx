import { getRouteApi } from '@tanstack/react-router'
import { BlockDetail } from '@/components/blocks'

const route = getRouteApi('/blocks/$height')

export default function BlockDetailPage() {
  const { height } = route.useParams()
  return <BlockDetail key={height} heightParam={height} />
}
