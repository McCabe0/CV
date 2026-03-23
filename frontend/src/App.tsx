import { useEffect, useState } from 'react'
import { getHello } from './api'

export default function App() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    getHello().then((data) => {
      setMessage(data.message)
    })
  }, [])

  return (
    <div style={{ padding: 20 }}>
      <h1>Skill2Career</h1>
      <p>{message}</p>
    </div>
  )
}