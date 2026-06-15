import { useState } from 'react'

function ResultDisplay({ result, onReset }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(result.shortUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Fallback for older browsers
      const textArea = document.createElement('textarea')
      textArea.value = result.shortUrl
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <div className="mt-8 max-w-2xl mx-auto bg-white rounded-lg shadow-md p-6">
      <div className="flex items-center gap-2 mb-4">
        <span className="text-green-500 text-xl">✅</span>
        <h3 className="text-lg font-semibold text-gray-800">Your short URL is ready!</h3>
      </div>

      <div className="flex items-center gap-3 mb-4">
        <input
          type="text"
          value={result.shortUrl}
          readOnly
          className="flex-1 px-4 py-2 bg-gray-50 border border-gray-200 rounded-lg font-mono text-indigo-600"
        />
        <button
          onClick={handleCopy}
          className="px-4 py-2 bg-indigo-100 text-indigo-700 font-medium rounded-lg hover:bg-indigo-200 transition-colors"
        >
          {copied ? '✓ Copied!' : '📋 Copy Link'}
        </button>
      </div>

      <div className="text-sm text-gray-500 space-y-1">
        <p><span className="font-medium">Original:</span> {result.originalUrl}</p>
        <p><span className="font-medium">Created:</span> {new Date(result.createdAt).toLocaleString()}</p>
      </div>

      <button
        onClick={onReset}
        className="mt-4 text-indigo-600 hover:text-indigo-800 font-medium"
      >
        ← Shorten Another URL
      </button>
    </div>
  )
}

export default ResultDisplay
