import { getRouteApi } from '@tanstack/react-router'
import { TransactionDetail } from '@/components/tx'

const route = getRouteApi('/tx/$id')

export default function TransactionPage() {
  const { id } = route.useParams()
  return <TransactionDetail key={id} id={id} />
}
