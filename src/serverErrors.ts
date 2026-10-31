// The server answers in Swedish. Other languages get their text here, so an error never shows in the wrong language.
type Lang = 'sv' | 'da' | 'en';

const translations: Record<string, Record<Exclude<Lang, 'sv'>, string>> = {
  'Ange koden från det andra teamet.': { da: 'Angiv koden fra det andet team.', en: 'Enter the code from the other team.' },
  'Bara gruppens skapare kan hämta koden.': { da: 'Kun gruppens opretter kan hente koden.', en: 'Only the group creator can get the code.' },
  'Bara gruppens skapare kan koppla bort grupper.': { da: 'Kun gruppens opretter kan fjerne forbindelsen til grupper.', en: 'Only the group creator can unlink groups.' },
  'Bara gruppens skapare kan para ihop grupper.': { da: 'Kun gruppens opretter kan forbinde grupper.', en: 'Only the group creator can link groups.' },
  'De grupperna är redan ihopkopplade.': { da: 'De grupper er allerede forbundet.', en: 'Those groups are already linked.' },
  'Den här gruppen använder det äldre gruppsystemet.': { da: 'Denne gruppe bruger det ældre gruppesystem.', en: 'This group uses the older group system.' },
  'Det gick inte att gilla.': { da: 'Det lykkedes ikke at synes godt om.', en: 'Could not like that.' },
  'Det målet finns redan för den perioden.': { da: 'Det mål findes allerede for den periode.', en: 'That goal already exists for this period.' },
  'Det är er egen kod. Använd koden från det andra teamet.': { da: 'Det er jeres egen kode. Brug koden fra det andet team.', en: 'That is your own code. Use the code from the other team.' },
  'Du använder ett gammalt formulär. Ladda om sidan för att välja namn och mål.': { da: 'Du bruger en gammel formular. Genindlæs siden for at vælge navn og mål.', en: 'You are using an old form. Reload the page to choose a name and goals.' },
  'Du kan inte gå med i den här gruppen igen.': { da: 'Du kan ikke deltage i denne gruppe igen.', en: 'You cannot join this group again.' },
  'Du kan inte ta bort den här personen.': { da: 'Du kan ikke fjerne denne person.', en: 'You cannot remove this person.' },
  'Du tillhör inte den här gruppen.': { da: 'Du tilhører ikke denne gruppe.', en: 'You are not a member of this group.' },
  'Du är inte med i den här gruppen.': { da: 'Du er ikke med i denne gruppe.', en: 'You are not in this group.' },
  'En grupp kan vara ihopkopplad med högst fem grupper.': { da: 'En gruppe kan være forbundet med højst fem grupper.', en: 'A group can be linked to at most five groups.' },
  'Endast gruppens skapare kan ändra mål.': { da: 'Kun gruppens opretter kan ændre mål.', en: 'Only the group creator can change goals.' },
  'För många försök. Vänta en stund och försök igen.': { da: 'For mange forsøg. Vent et øjeblik og prøv igen.', en: 'Too many attempts. Wait a moment and try again.' },
  'GPS-positioner får inte skickas till servern.': { da: 'GPS-positioner må ikke sendes til serveren.', en: 'GPS positions must not be sent to the server.' },
  'Gruppnamnet måste vara 3 till 50 tecken.': { da: 'Gruppens navn skal være 3 til 50 tegn.', en: 'The group name must be 3 to 50 characters.' },
  'Inbjudan hittades inte.': { da: 'Invitationen blev ikke fundet.', en: 'The invitation was not found.' },
  'Inloggning är inte konfigurerad på servern.': { da: 'Login er ikke konfigureret på serveren.', en: 'Sign-in is not configured on the server.' },
  'Inloggningen kunde inte verifieras.': { da: 'Login kunne ikke verificeres.', en: 'The sign-in could not be verified.' },
  'Koden hittades inte. Be det andra teamet om en ny kod.': { da: 'Koden blev ikke fundet. Bed det andet team om en ny kode.', en: 'The code was not found. Ask the other team for a new code.' },
  'Logga in för att gå med i den här gruppen.': { da: 'Log ind for at deltage i denne gruppe.', en: 'Sign in to join this group.' },
  'Logga in med e-post, Google eller Apple.': { da: 'Log ind med e-mail, Google eller Apple.', en: 'Sign in with email, Google or Apple.' },
  'Länken är ogiltig.': { da: 'Linket er ugyldigt.', en: 'The link is invalid.' },
  'Namnet måste vara 3 till 32 tecken.': { da: 'Navnet skal være 3 til 32 tegn.', en: 'The name must be 3 to 32 characters.' },
  'Ogiltig GPS-sträcka.': { da: 'Ugyldig GPS-strækning.', en: 'Invalid GPS distance.' },
  'Ogiltigt mål.': { da: 'Ugyldigt mål.', en: 'Invalid goal.' },
  'Ogiltigt meddelande.': { da: 'Ugyldig besked.', en: 'Invalid request.' },
  'Serverfel. Försök igen.': { da: 'Serverfejl. Prøv igen.', en: 'Server error. Try again.' },
  'Sessionen hittades inte. Ange ett namn igen.': { da: 'Sessionen blev ikke fundet. Angiv et navn igen.', en: 'The session was not found. Enter a name again.' },
  'Sessionen hittades inte.': { da: 'Sessionen blev ikke fundet.', en: 'The session was not found.' },
  'Välj ett namn i gruppen på 3 till 32 tecken.': { da: 'Vælg et navn i gruppen på 3 til 32 tegn.', en: 'Choose a name for the group, 3 to 32 characters.' },
  'Välj minst ett giltigt gruppmål.': { da: 'Vælg mindst ét gyldigt gruppemål.', en: 'Choose at least one valid group goal.' },
  'Användarnamnet är upptaget.': { da: 'Brugernavnet er optaget.', en: 'That username is already taken.' },
  'Användarnamnet måste vara 3 till 20 tecken: bokstäver, siffror, punkt, understreck eller bindestreck.': { da: 'Brugernavnet skal være 3 til 20 tegn: bogstaver, tal, punktum, understreg eller bindestreg.', en: 'The username must be 3 to 20 characters: letters, digits, dot, underscore or hyphen.' },
  'Lösenordet måste vara 8 till 72 tecken.': { da: 'Adgangskoden skal være 8 til 72 tegn.', en: 'The password must be 8 to 72 characters.' },
  'Kunde inte skapa kontot. Försök igen.': { da: 'Kontoen kunne ikke oprettes. Prøv igen.', en: 'The account could not be created. Try again.' },
  'Konton med användarnamn är inte påslagna på servern.': { da: 'Konti med brugernavn er ikke slået til på serveren.', en: 'Accounts with a username are not enabled on the server.' },
  'Vänta på nästa mätning.': { da: 'Vent på næste måling.', en: 'Wait for the next measurement.' },
};

// Unknown messages stay as they are, so a new server message still shows something readable.
export function translateServerError(message: string | undefined, lang: Lang): string {
  if (!message) return '';
  if (lang === 'sv') return message;
  return translations[message]?.[lang] ?? message;
}
