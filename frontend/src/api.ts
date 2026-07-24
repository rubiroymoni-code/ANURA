const API = import.meta.env.VITE_API_URL || '/api/v1';
export type EntryType='WORKOUT'|'MEAL'|'WEIGHT'|'MEASUREMENT'|'GOAL';
export type Entry={id:string;type:EntryType;title:string;entryDate:string;value?:number;unit?:string;details?:string;notes?:string;completed:boolean};
export type User={id:string;email:string;displayName:string};

async function request<T>(path:string, init:RequestInit={}):Promise<T>{
  const token=localStorage.getItem('anura-token');
  const response=await fetch(`${API}${path}`,{...init,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{}) ,...init.headers}});
  if(!response.ok){const text=await response.text();throw new Error(text||'No se pudo completar la operación');}
  return response.status===204 ? undefined as T : response.json();
}
export const api={
  auth:(mode:'login'|'register',data:object)=>request<{token:string;user:User}>(`/auth/${mode}`,{method:'POST',body:JSON.stringify(data)}),
  entries:(type?:EntryType)=>request<Entry[]>(`/entries${type?`?type=${type}`:''}`),
  create:(data:Omit<Entry,'id'>)=>request<Entry>('/entries',{method:'POST',body:JSON.stringify(data)}),
  remove:(id:string)=>request<void>(`/entries/${id}`,{method:'DELETE'})
};
