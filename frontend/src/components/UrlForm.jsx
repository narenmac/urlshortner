import { useState } from 'react'

function UrlForm({ onSubmit, loading }) {
  const [url, setUrl] = useState('')
  const [validationError, setValidationError] = useState('')

  const validateUrl = (value) => {
    try {
      const parsed = new URL(value)
      if (!['http:', 'https:'].includes(parsed.protocol)) {
        return 'URL must start with http:// or https://'
      }
      if (value.length > 2048) {
        return 'URL exceeds maximum length of 2048 characters'
      }
      return ''
    } catch {
      return 'Please enter a valid URL starting with http:// or https://'
    }
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    const error = validateUrl(url)
    if (error) {
      setValidationError(error)
      return
    }
    setValidationError('')
    onSubmit(url)
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-3 max-w-2xl mx-auto">
      <div className="flex-1">
        <input
          type="text"
          value={url}
          onChange={(e) => { setUrl(e.target.value); setValidationError('') }}
          placeholder="https://www.example.com/very/long/url/path..."
          className={`w-full px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
            validationError ? 'border-red-500' : 'border-gray-300'
          }`}
          disabled={loading}
        />
        {validationError && (
          <p className="mt-2 text-sm text-red-600">⚠️ {validationError}</p>
        )}
      </div>
      <button
        type="submit"
        disabled={loading || !url.trim()}
        className="px-6 py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
      >
        {loading ? '...' : 'GO!'}
      </button>
    </form>
  )
}

export default UrlForm
