import { jsPDF } from 'jspdf'
import type { CvResponse, Profile } from './api'

type RGB = [number, number, number]

const INK: RGB = [31, 41, 55]
const ACCENT: RGB = [79, 70, 229]
const HEADING: RGB = [55, 48, 163]
const MUTED: RGB = [100, 116, 139]
const RULE: RGB = [199, 210, 254]

/**
 * Renders the (possibly edited) CV into a clean, selectable-text A4 PDF and
 * triggers a download in the browser.
 */
export function downloadCvPdf(cv: CvResponse, profile: Profile | null) {
  const doc = new jsPDF({ unit: 'pt', format: 'a4' })
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  const margin = 48
  const contentWidth = pageWidth - margin * 2
  let y = margin

  const ensureSpace = (needed: number) => {
    if (y + needed > pageHeight - margin) {
      doc.addPage()
      y = margin
    }
  }

  const writeText = (
    text: string,
    opts: { size?: number; bold?: boolean; gap?: number; color?: RGB } = {},
  ) => {
    const { size = 11, bold = false, gap = 6, color = INK } = opts
    doc.setFont('helvetica', bold ? 'bold' : 'normal')
    doc.setFontSize(size)
    doc.setTextColor(...color)
    const lineHeight = size * 1.35
    const lines = doc.splitTextToSize(text, contentWidth) as string[]
    lines.forEach((line) => {
      ensureSpace(lineHeight)
      doc.text(line, margin, y)
      y += lineHeight
    })
    y += gap
  }

  const writeHeading = (text: string) => {
    ensureSpace(28)
    writeText(text.toUpperCase(), { size: 12, bold: true, gap: 2, color: HEADING })
    doc.setDrawColor(...RULE)
    doc.line(margin, y - 4, pageWidth - margin, y - 4)
    y += 8
  }

  const name = profile?.name?.trim() || 'Your Name'
  writeText(name, { size: 22, bold: true, gap: 2 })
  if (cv.headline.trim()) writeText(cv.headline, { size: 12, gap: 4, color: ACCENT })

  const clean = (bit?: string) => bit?.trim()
  const isPresent = (bit: string | undefined): bit is string => Boolean(bit)

  const contactBits = [clean(profile?.email), clean(profile?.phone), clean(profile?.location)].filter(isPresent)
  if (contactBits.length > 0) {
    writeText(contactBits.join('   •   '), { size: 10, gap: 2, color: MUTED })
  }

  const linkBits = [clean(profile?.linkedin), clean(profile?.portfolio), clean(profile?.workAuthorization)].filter(isPresent)
  if (linkBits.length > 0) {
    writeText(linkBits.join('   •   '), { size: 10, gap: 10, color: MUTED })
  }

  if (cv.summary.trim()) {
    writeHeading('Summary')
    writeText(cv.summary, { gap: 10 })
  }

  if (cv.keySkills.length > 0) {
    writeHeading('Key Skills')
    writeText(cv.keySkills.join('   •   '), { gap: 10 })
  }

  if (cv.experienceBullets.length > 0) {
    writeHeading('Experience')
    cv.experienceBullets.forEach((bullet) => writeText(`•  ${bullet}`, { gap: 3 }))
    y += 7
  }

  if (cv.educationSection.trim()) {
    writeHeading('Education')
    writeText(cv.educationSection, { gap: 10 })
  }

  if (cv.atsKeywords.length > 0) {
    writeHeading('ATS Keywords')
    writeText(cv.atsKeywords.join(', '), { size: 10, gap: 6, color: MUTED })
  }

  doc.save(`${name.replace(/\s+/g, '_')}_CV.pdf`)
}
