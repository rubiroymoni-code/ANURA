export const API_BASE = import.meta.env.VITE_API_URL || "/api/v1";
export type EntryType = "WORKOUT" | "MEAL" | "WEIGHT" | "MEASUREMENT" | "GOAL";
export type Entry = {
  id: string;
  type: EntryType;
  title: string;
  entryDate: string;
  value?: number;
  unit?: string;
  details?: string;
  notes?: string;
  completed: boolean;
};
export type User = { id: string; email: string; displayName: string };

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("anura-token");
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...(init.body instanceof FormData
        ? {}
        : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "No se pudo completar la operación");
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export const api = {
  auth: (mode: "login" | "register", data: object) =>
    request<{ token: string; user: User }>(`/auth/${mode}`, {
      method: "POST",
      body: JSON.stringify(data),
    }),
  entries: (type?: EntryType) =>
    request<Entry[]>(`/entries${type ? `?type=${type}` : ""}`),
  create: (data: Omit<Entry, "id">) =>
    request<Entry>("/entries", { method: "POST", body: JSON.stringify(data) }),
  remove: (id: string) => request<void>(`/entries/${id}`, { method: "DELETE" }),
};
export type ImportIssue = {
  row?: number;
  column?: string;
  code: string;
  message: string;
  severity: string;
};
export type ImportPreview = {
  importJobId: string;
  status: string;
  confirmable: boolean;
  planExternalId: string;
  planName: string;
  version: number;
  weeks: number;
  days: number;
  exercises: number;
  validFrom?: string;
  validUntil?: string;
  issues: ImportIssue[];
};
export const trainingApi = {
  preview: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<ImportPreview>("/imports/training-plans/preview", {
      method: "POST",
      body,
    });
  },
  confirm: (id: string) =>
    request<{ planId: string; status: string }>(`/imports/${id}/confirm`, {
      method: "POST",
    }),
  plans: () =>
    request<
      Array<{ id: string; name: string; version: number; status: string }>
    >("/workout-plans"),
};
export type Household = { id: string; name: string; role: string };
export type NutritionImportPreview = {
  importJobId: string;
  status: string;
  confirmable: boolean;
  planName?: string;
  version?: number;
  rows: number;
  recipes: number;
  ingredients: number;
  users: string[];
  issues: Array<{ row?: number; column?: string; message: string }>;
};
export const householdApi = {
  list: () => request<Household[]>("/households"),
  create: (name: string) =>
    request<Household>("/households", {
      method: "POST",
      body: JSON.stringify({ name }),
    }),
  members: (id: string) =>
    request<
      Array<{ id: string; email: string; display_name: string; role: string }>
    >(`/households/${id}/members`),
  invite: (id: string, email: string) =>
    request<{ code: string; expiresAt: string }>(
      `/households/${id}/invitations`,
      { method: "POST", body: JSON.stringify({ email }) },
    ),
  accept: (code: string) =>
    request<void>("/households/invitations/accept", {
      method: "POST",
      body: JSON.stringify({ code }),
    }),
};
export const nutritionApi = {
  recipes: () =>
    request<
      Array<{ id: string; code: string; name: string; servings: number }>
    >("/nutrition/recipes"),
  plans: () =>
    request<
      Array<{ id: string; name: string; version: number; status: string }>
    >("/nutrition/plans"),
  week: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/plans/${id}/week`),
  summary: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/plans/${id}/summary`),
  activate: (id: string) =>
    request<void>(`/nutrition/plans/${id}/activate`, { method: "POST" }),
  recipe: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/recipes/${id}`),
  shopping: () =>
    request<
      Array<{
        id: string;
        week_number: number;
        status: string;
        manually_modified: boolean;
      }>
    >("/nutrition/shopping-lists"),
  generateShopping: (planId: string, replaceModified = false) =>
    request<{ id: string; items: number }>(
      `/nutrition/plans/${planId}/shopping-list?week=1&replaceModified=${replaceModified}`,
      { method: "POST" },
    ),
  items: (id: string) =>
    request<
      Array<{
        id: string;
        name: string;
        category: string;
        quantity: number;
        unit: string;
        purchased: boolean;
      }>
    >(`/nutrition/shopping-lists/${id}/items`),
  toggle: (id: string) =>
    request<void>(`/nutrition/shopping-items/${id}/toggle`, {
      method: "PATCH",
    }),
  preview: (type: "diet" | "shared-diet" | "recipes", file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<NutritionImportPreview>(
      `/imports/nutrition/${type}/preview`,
      { method: "POST", body },
    );
  },
  confirm: (id: string) =>
    request<{ status: string }>(`/imports/nutrition/${id}/confirm`, {
      method: "POST",
    }),
};
