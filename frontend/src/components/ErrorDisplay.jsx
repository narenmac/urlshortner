function ErrorDisplay({ message }) {
  return (
    <div className="mt-6 max-w-2xl mx-auto bg-red-50 border border-red-200 rounded-lg p-4">
      <p className="text-red-700">❌ {message}</p>
    </div>
  )
}

export default ErrorDisplay
