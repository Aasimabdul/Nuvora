export type ProviderId="radio"|"audius"|"youtube"|"spotify"|"jiosaavn";
export type Track={id:string;title:string;artist:string;artwork?:string;streamUrl?:string;pageUrl?:string;provider:ProviderId;playable:boolean};
export const providers:{id:ProviderId;label:string;mode:string;url?:string}[]=[
{id:"radio",label:"Direct streams",mode:"In-app playback"},{id:"audius",label:"Audius",mode:"Public API search"},
{id:"youtube",label:"YouTube Music",mode:"Official-service handoff",url:"https://music.youtube.com/"},
{id:"spotify",label:"Spotify",mode:"Official-service handoff",url:"https://open.spotify.com/"},
{id:"jiosaavn",label:"JioSaavn",mode:"Official-service handoff",url:"https://www.jiosaavn.com/"}];
export const radioStations:Track[]=[
{id:"rp",title:"Radio Paradise",artist:"Eclectic mix · MP3 stream",streamUrl:"https://stream.radioparadise.com/mp3-128",provider:"radio",playable:true},
{id:"groove",title:"Groove Salad",artist:"SomaFM · Ambient / downtempo",streamUrl:"https://ice.somafm.com/groovesalad",provider:"radio",playable:true},
{id:"drone",title:"Drone Zone",artist:"SomaFM · Atmospheric ambient",streamUrl:"https://ice.somafm.com/dronezone",provider:"radio",playable:true}];
export async function searchAudius(query:string,signal?:AbortSignal):Promise<Track[]>{
if(!query.trim())return[];const url=new URL("https://api.audius.co/v1/tracks/search");url.searchParams.set("query",query.trim());url.searchParams.set("app_name","NuvoraDesktop");
const response=await fetch(url,{signal,headers:{Accept:"application/json"}});if(!response.ok)throw new Error(`Audius search failed (HTTP ${response.status}). Try again.`);
const payload:unknown=await response.json();if(!payload||typeof payload!=="object"||!("data"in payload)||!Array.isArray((payload as {data:unknown}).data))throw new Error("Audius returned an unexpected response.");
return((payload as {data:Record<string,unknown>[]}).data).flatMap((item):Track[]=>{const id=item.id,title=item.title,user=item.user as Record<string,unknown>|undefined,art=item.artwork as Record<string,unknown>|undefined;if((typeof id!=="string"&&typeof id!=="number")||typeof title!=="string")return[];return[{id:String(id),title,artist:typeof user?.name==="string"?user.name:"Unknown artist",artwork:typeof art?.["150x150"]==="string"?art["150x150"]:undefined,pageUrl:typeof item.permalink==="string"?`https://audius.co${item.permalink}`:"https://audius.co/",provider:"audius",playable:false}];});}
export function officialSearchUrl(provider:ProviderId,query:string){const q=encodeURIComponent(query.trim());if(provider==="youtube")return `https://music.youtube.com/search?q=${q}`;if(provider==="spotify")return `https://open.spotify.com/search/${q}`;if(provider==="jiosaavn")return `https://www.jiosaavn.com/search/${q}`;return undefined;}