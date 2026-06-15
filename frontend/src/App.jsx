import { useState } from 'react'
import UrlForm from './components/UrlForm'
import ResultDisplay from './components/ResultDisplay'
import ErrorDisplay from './components/ErrorDisplay'
import { shortenUrl } from './services/api'

function App() {
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const handleShorten = async (url) => {
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await shortenUrl(url)
      setResult(data)
    } catch (err) {
      setError(err.message || 'An unexpected error occurred')
    } finally {
      setLoading(false)
    }
  }

  const handleReset = () => {
    setResult(null)
    setError(null)
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      <header className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-2xl font-bold text-indigo-600">🔗 URL Shortener</h1>
          <div className="flex gap-4">
            <a href="#" className="text-gray-600 hover:text-indigo-600">About</a>
            <a href="#" className="text-gray-600 hover:text-indigo-600">GitHub</a>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-16">
        <div className="text-center mb-12">
          <h2 className="text-4xl font-bold text-gray-800 mb-4">Shorten Your URLs</h2>
          <p className="text-lg text-gray-600">Make long links short &amp; shareable</p>
        </div>

        <UrlForm onSubmit={handleShorten} loading={loading} />

        {error && <ErrorDisplay message={error} />}
        {result && <ResultDisplay result={result} onReset={handleReset} />}
      </main>

      <footer className="fixed bottom-0 w-full bg-white border-t py-4">
        <div className="max-w-4xl mx-auto px-4 text-center text-gray-500 text-sm">
          © 2026 URL Shortener | Powered by Azure
        </div>
      </footer>
    </div>
  )
}

export default App
