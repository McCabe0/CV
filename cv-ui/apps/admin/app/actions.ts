'use server'

import { revalidatePath } from 'next/cache'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { BASE_PATH } from './basePath'
import {
  readCompanyForm,
  type Company,
  type CompanyErrors,
  type CompanyValues,
  type Source,
} from './companies'

const SORRY = 'Sorry, something went wrong. Try again later.'

/** The add form: what was entered and what is wrong with it, or the company just added. */
export type AddState = {
  values?: CompanyValues
  errors?: CompanyErrors
  formError?: string
  added?: string
}

/** What a company's button did, or why it could not. */
export type RowState = { message?: string; error?: string }

/** The api's refusals worded for the admin: no such company, a conflict, a bad board, a board down. */
const EXPLAINED = [404, 409, 422, 502]

/** Why the api refused, in its own words when it worded them for the admin; otherwise sorry. */
const refusal = (error: unknown, status: number) =>
  (EXPLAINED.includes(status) && (error as { detail?: string } | undefined)?.detail) || SORRY

const client = async () => apiClient((await requireSession(BASE_PATH)).accessToken)

/** Asks the api to track a board. It reads the board first, so it can refuse it. */
async function track(body: { name: string; source: Source; boardToken: string }) {
  try {
    const { data, error, response } = await (await client()).POST('/admin/companies', { body })
    if (!data) return { error: refusal(error, response.status) }
    revalidatePath('/')
    return {}
  } catch {
    return { error: SORRY }
  }
}

/** Adds a company from the form, once it is right. */
export async function addCompany(_previous: AddState, data: FormData): Promise<AddState> {
  const { values, errors } = readCompanyForm(data)
  if (Object.keys(errors).length > 0) return { values, errors }
  const { error } = await track({ ...values, source: values.source as Source })
  return error ? { values, formError: error } : { added: values.name }
}

/** Tracks a company again, as it was. Bound to its company on the page. */
export async function trackAgain(
  company: Company,
  _previous: RowState,
  _data: FormData,
): Promise<RowState> {
  const { name, source, boardToken } = company
  const { error } = await track({ name, source, boardToken })
  return error
    ? { error }
    : { message: `Tracking ${name} again. Its jobs will be back within a minute.` }
}

/** Stops tracking a company. Bound to its company on the page. */
export async function stopTracking(
  id: number,
  name: string,
  _previous: RowState,
  _data: FormData,
): Promise<RowState> {
  try {
    const { data, error, response } = await (
      await client()
    ).DELETE('/admin/companies/{id}', {
      params: { path: { id } },
    })
    if (!data) return { error: refusal(error, response.status) }
    revalidatePath('/')
    return { message: `Stopped tracking ${name}. Its jobs are closed.` }
  } catch {
    return { error: SORRY }
  }
}

/** Queues a company's crawl now. Bound to its company on the page. */
export async function crawlNow(
  id: number,
  name: string,
  _previous: RowState,
  _data: FormData,
): Promise<RowState> {
  try {
    const { error, response } = await (
      await client()
    ).POST('/admin/companies/{id}/crawl', {
      params: { path: { id } },
    })
    if (response.status !== 202) return { error: refusal(error, response.status) }
    return { message: `Crawling ${name} now. Refresh the page in a minute to see how it went.` }
  } catch {
    return { error: SORRY }
  }
}
