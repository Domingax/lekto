package app.lekto.text

/**
 * French, the second language the app ships (ADR-0034): a complete table of the
 * app's own texts, defined against the English base, so choosing French in
 * **Settings → App language** re-renders the whole interface with no relaunch.
 */
internal val FrenchTexts: AppTexts by lazy { AppTexts(FrenchTable, FrenchLanguageNames, EnglishTable) }

private val FrenchTable: Map<TextKey, String> = mapOf(
    // Navigation and controls.
    TextKey.LIBRARY to "Bibliothèque",
    TextKey.SETTINGS to "Réglages",
    TextKey.VOCABULARY to "Vocabulaire",
    TextKey.LANGUAGES to "Langues",
    TextKey.ATTRIBUTION to "Attribution",
    TextKey.BACK to "Retour",
    TextKey.DISMISS to "Fermer",
    TextKey.DELETE to "Supprimer",
    TextKey.CANCEL to "Annuler",
    TextKey.SAVE to "Enregistrer",
    TextKey.SAVED to "Enregistré",
    TextKey.CLOSE to "Fermer",
    TextKey.SHOW to "Afficher",
    TextKey.HIDE to "Masquer",
    TextKey.SEARCH to "Rechercher",
    TextKey.CLEAR_SEARCH to "Effacer la recherche",

    // Platform file dialogs.
    TextKey.PICK_BOOK_TITLE to "Importer un livre",
    TextKey.EXPORT_VAULT_TITLE to "Exporter le coffre",
    TextKey.IMPORT_VAULT_TITLE to "Importer un coffre",

    // Library.
    TextKey.LIBRARY_EMPTY_TITLE to "Aucun livre pour l'instant",
    TextKey.LIBRARY_EMPTY to "Un EPUB ou un fichier texte, et le premier mot est à une minute.",
    TextKey.IMPORT_BOOK to "Importer un livre",
    TextKey.LIBRARY_LOADING to "Chargement de vos livres",
    TextKey.CONTINUE_READING to "Continuer la lecture",
    TextKey.OTHER_BOOKS to "Autres livres",
    TextKey.IMPORT to "Importer",
    TextKey.IMPORTING to "Importation…",
    TextKey.IMPORT_PROGRESS to "Progression de l'importation",
    TextKey.DELETE_BOOK_TITLE to "Supprimer « {0} » ?",
    TextKey.DELETE_BOOK_MESSAGE to "Lekto supprimera « {0} ». Cette action est irréversible.",
    TextKey.ERROR_DETAIL to "Erreur : {0}",
    TextKey.NO_BOOKS_MATCH_QUERY to "Aucun livre ne correspond à « {0} ».",
    TextKey.LIBRARY_ROW_PROGRESS to "{0} · {1} %",
    TextKey.LIBRARY_ROW_NOT_STARTED to "{0} · Non commencé",
    TextKey.LIBRARY_ROW_MEASURING to "{0} · …",
    TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION to "La progression de lecture est en cours de calcul.",

    // Vocabulary.
    TextKey.VOCABULARY_EMPTY to "Aucun mot enregistré — touchez un mot pendant la lecture pour l'enregistrer",
    TextKey.ALL_LEVELS to "Tous les niveaux",
    TextKey.MASTERY_UNKNOWN to "Inconnu",
    TextKey.MASTERY_RECOGNIZED to "Reconnu",
    TextKey.MASTERY_FAMILIAR to "Familier",
    TextKey.MASTERY_LEARNED to "Appris",
    TextKey.MASTERY_KNOWN to "Maîtrisé",
    TextKey.NO_WORDS_TO_SHOW to "Aucun mot à afficher.",
    TextKey.NO_WORDS_MATCH_QUERY to "Aucun mot ne correspond à « {0} ».",
    TextKey.NO_WORDS_MATCH_QUERY_AT_LEVEL to "Aucun mot ne correspond à « {0} » au niveau {1}.",
    TextKey.NO_WORDS_AT_LEVEL to "Aucun mot au niveau {0}.",

    // Reader.
    TextKey.PAGE_OF to "Page {0} sur {1}",
    TextKey.READING_PREFERENCES to "Aa",
    TextKey.READING_TEXT to "Texte",
    TextKey.READING_FACE to "Police du texte",
    TextKey.READING_FACE_SERIF to "À empattements",
    TextKey.READING_FACE_SANS_SERIF to "Sans empattements",
    TextKey.READING_SIZE to "Taille du texte",
    TextKey.READING_SMALLER to "Réduire",
    TextKey.READING_LARGER to "Agrandir",
    TextKey.READING_MARGINS to "Marges",
    TextKey.READING_MARGIN_NARROW to "Étroites",
    TextKey.READING_MARGIN_COMFORTABLE to "Confortables",
    TextKey.READING_MARGIN_WIDE to "Larges",

    // Word lookup panel.
    TextKey.LISTEN to "Écouter",
    TextKey.MASTERY to "Maîtrise",
    TextKey.NOT_IN_DICTIONARY to "« {0} » n'est pas dans le dictionnaire hors ligne.",
    TextKey.NO_VOICE_FOR_LANGUAGE to "Aucune voix n'est installée pour « {0} ».",
    TextKey.NO_VOICE_FOR_UNKNOWN_LANGUAGE to "Aucune voix n'est installée pour cette langue.",
    TextKey.LOOK_IT_UP_ONLINE to "Chercher en ligne",
    TextKey.TRANSLATE_ONLINE to "Traduire en ligne",

    // Phrase lookup panel.
    TextKey.CONNECT_PROVIDER_PROMPT to
        "Connectez un fournisseur LLM dans les réglages pour traduire des phrases ici.",
    TextKey.OPEN_SETTINGS to "Ouvrir les réglages",
    TextKey.TRANSLATING to "Traduction…",
    TextKey.PROVIDER_RETURNED_NO_TRANSLATION to "Le fournisseur n'a renvoyé aucune traduction.",
    TextKey.HIDE_WHAT_IS_SENT to "Masquer ce qui est envoyé",
    TextKey.WHAT_IS_SENT to "Qu'est-ce qui est envoyé ?",
    TextKey.SENDS_SELECTION_TO to "Envoie votre sélection et sa phrase à {0}.",
    TextKey.THE_PROVIDER to "le fournisseur",
    TextKey.NO_TRANSLATION_SERVICE to "Aucun service de traduction n'est disponible pour cette phrase.",

    // Settings.
    TextKey.SECTION_APPEARANCE to "Apparence",
    TextKey.SECTION_APP_LANGUAGE to "Langue de l'application",
    TextKey.SECTION_LANGUAGES to "Langues",
    TextKey.SECTION_VAULT to "Coffre",
    TextKey.SECTION_SYNC to "Synchronisation",
    TextKey.SECTION_LLM_PROVIDER to "Fournisseur LLM",
    TextKey.LANGUAGES_SECTION_DESCRIPTION to
        "Découvrez les langues que Lekto propose pour la lecture, classées selon celles où vous avez le " +
        "plus de livres. Définissez votre niveau et téléchargez les données de difficulté des mots et " +
        "le dictionnaire hors ligne pour chacune.",
    TextKey.VAULT_SECTION_DESCRIPTION to
        "Votre coffre contient tout ce que vous avez importé et enregistré. Exportez-le dans un fichier " +
        "que vous conservez, ou importez-en un pour restaurer vos livres et votre vocabulaire sur cet " +
        "appareil. L'importation remplace le coffre déjà présent sur cet appareil.",
    TextKey.VAULT_UNAVAILABLE to "L'export et l'import du coffre ne sont pas disponibles dans cette version.",
    TextKey.EXPORT_VAULT to "Exporter le coffre",
    TextKey.IMPORT_VAULT to "Importer un coffre",
    TextKey.PROVIDER_SECTION_DESCRIPTION to
        "Traduisez une phrase sélectionnée avec votre propre fournisseur LLM. Votre clé est conservée " +
        "dans le stockage sécurisé de cet appareil — jamais dans votre coffre, un export ou un journal.",
    TextKey.PROVIDER_UNAVAILABLE to
        "La connexion à un fournisseur LLM n'est pas disponible dans cette version.",
    TextKey.BASE_URL to "URL de base",
    TextKey.API_KEY to "Clé API",
    TextKey.MODEL to "Modèle",
    TextKey.CHOOSE_A_PROVIDER to "Choisir un fournisseur",
    TextKey.KEY_STORED to "Une clé est enregistrée.",
    TextKey.REMOVE_KEY to "Supprimer la clé",
    TextKey.TEST_CONNECTION to "Tester la connexion",
    TextKey.APP_LANGUAGE_DESCRIPTION to "La langue que Lekto utilise pour ses propres textes.",
    TextKey.APPEARANCE_DESCRIPTION to
        "Choisissez l'apparence de Lekto sur cet appareil. Le thème recolore toute l'application — " +
        "jamais le texte d'un livre, qui garde sa police, sa taille et ses marges. Votre choix reste " +
        "sur cet appareil et ne voyage jamais avec votre coffre.",
    TextKey.THEME_SYSTEM to "Système",
    TextKey.THEME_SYSTEM_DESCRIPTION to "Suit le réglage de l'appareil",
    TextKey.THEME_LIGHT to "Clair",
    TextKey.THEME_LIGHT_DESCRIPTION to "Papier chaud, encre noire",
    TextKey.THEME_DARK to "Sombre",
    TextKey.THEME_DARK_DESCRIPTION to "Nuit chaude, encre claire",
    TextKey.THEME_SEPIA to "Sépia",
    TextKey.THEME_SEPIA_DESCRIPTION to "Papier chiffon",
    TextKey.CURRENT_THEME to "Actuel",

    // Sync.
    TextKey.SYNC_SECTION_DESCRIPTION to
        "Synchronisez votre coffre entre vos appareils via votre propre serveur WebDAV — Nextcloud, " +
        "ownCloud, Synology ou tout hôte WebDAV. Votre mot de passe est conservé dans le stockage " +
        "sécurisé de cet appareil.",
    TextKey.SYNC_FOLDER_WARNING to
        "Ne pointez pas un outil de synchronisation de dossiers vers votre coffre. Syncthing, Dropbox, " +
        "iCloud Drive et similaires ajouteraient un second écrivain sur les mêmes fichiers et " +
        "pourraient les corrompre. Utilisez la synchronisation propre à Lekto.",
    TextKey.SYNC_UNAVAILABLE to "La synchronisation n'est pas disponible dans cette version.",
    TextKey.SERVER_ADDRESS to "Adresse du serveur",
    TextKey.USERNAME to "Nom d'utilisateur",
    TextKey.APPLICATION_PASSWORD to "Mot de passe d'application",
    TextKey.TURN_OFF_SYNC to "Désactiver la synchronisation",
    TextKey.ENABLE_SYNC to "Activer la synchronisation",
    TextKey.SYNC_NOW to "Synchroniser maintenant",
    TextKey.DISCONNECT to "Déconnecter",
    TextKey.SYNC_STATUS_ON to "La synchronisation est active.",
    TextKey.SYNC_STATUS_OFF to "La synchronisation est inactive.",
    TextKey.SYNC_STATUS_NOT_SET_UP to "La synchronisation n'est pas encore configurée.",

    // Languages surface.
    TextKey.RELOAD_LIST to "Recharger la liste",
    TextKey.TRANSLATION_LANGUAGE to "Langue de traduction",
    TextKey.NOT_SET to "Non défini",
    TextKey.LANGUAGES_EMPTY_AVAILABLE to "Lekto ne propose encore aucune langue pour la lecture.",
    TextKey.LANGUAGES_EMPTY_UNAVAILABLE to
        "Lekto n'a pas encore chargé la liste des langues qu'il propose. Rechargez la liste ou vérifiez " +
        "votre connexion.",
    TextKey.LOADING_LANGUAGES to "Chargement de la liste des langues…",
    TextKey.DIFFICULTY_NOT_PUBLISHED to
        "Lekto ne publie pas encore de données de difficulté des mots pour cette langue.",
    TextKey.DOWNLOADING_DIFFICULTY to "Téléchargement des données de difficulté des mots…",
    TextKey.DOWNLOAD_AGAIN to "Télécharger à nouveau",
    TextKey.DOWNLOAD_DIFFICULTY to "Télécharger les données de difficulté des mots",
    TextKey.DICTIONARY_NOT_PUBLISHED to "Lekto ne publie pas encore de dictionnaire hors ligne pour cette langue.",
    TextKey.DOWNLOADING_DICTIONARY to "Téléchargement du dictionnaire hors ligne…",
    TextKey.DOWNLOAD_DICTIONARY to "Télécharger le dictionnaire hors ligne",
    TextKey.DICTIONARY_STATUS_NOT_INSTALLED to
        "Le dictionnaire hors ligne n'est pas encore installé. Téléchargez-le pour chercher des mots " +
        "sans réseau.",
    TextKey.DICTIONARY_STATUS_READY to
        "Le dictionnaire hors ligne est installé. Les mots sont cherchés sans réseau.",
    TextKey.DICTIONARY_STATUS_INCOMPATIBLE to
        "Le dictionnaire installé est au format {0}, mais cette application lit le format {1}. " +
        "Téléchargez-le à nouveau.",
    TextKey.DICTIONARY_STATUS_CORRUPT to
        "Le dictionnaire installé est endommagé et n'a pas pu être lu. Téléchargez-le à nouveau.",
    TextKey.DIFFICULTY_STATUS_NOT_INSTALLED to
        "Les données de difficulté des mots ne sont pas encore installées. Téléchargez-les pour " +
        "colorer les mots selon leur difficulté.",
    TextKey.DIFFICULTY_STATUS_READY to
        "Les données de difficulté des mots sont installées. Les mots sont colorés selon leur " +
        "difficulté pour votre niveau.",
    TextKey.DIFFICULTY_STATUS_INCOMPATIBLE to
        "Les données de difficulté des mots installées sont au format {0}, mais cette application lit " +
        "le format {1}. Téléchargez-les à nouveau.",
    TextKey.DIFFICULTY_STATUS_CORRUPT to
        "Les données de difficulté des mots installées sont endommagées et n'ont pas pu être lues. " +
        "Téléchargez-les à nouveau.",

    // Attribution.
    TextKey.ATTRIBUTION_DICTIONARY_GRANT to
        "Les données du dictionnaire sont dérivées des contributeurs de Wiktionary, sous licence " +
        "CC BY-SA 4.0 (et GFDL à votre choix). Lekto n'est ni affilié à la Wikimedia Foundation ni à " +
        "Wiktionary, ni approuvé par eux.",
    TextKey.ATTRIBUTION_DICTIONARY_NOT_INSTALLED to
        "Le dictionnaire hors ligne n'est pas encore installé. Son attribution complète apparaîtra ici " +
        "une fois installé.",
    TextKey.ATTRIBUTION_DIFFICULTY_GRANT to
        "Les données de difficulté des mots sont dérivées de wordfreq par Robyn Speer, sous licence " +
        "CC BY-SA 4.0. Lekto n'est ni affilié à wordfreq ni approuvé par lui.",
    TextKey.ATTRIBUTION_DIFFICULTY_NOT_INSTALLED to
        "Les données de difficulté des mots ne sont pas encore installées. Leur attribution complète " +
        "apparaîtra ici une fois installées.",
    TextKey.FIELD_SOURCE to "Source",
    TextKey.FIELD_DATA to "Données",
    TextKey.FIELD_LICENCE to "Licence",
    TextKey.FIELD_MODIFICATIONS to "Modifications",

    // Messages.
    TextKey.MESSAGE_IMPORT_FAILED to "L'importation a échoué.",
    TextKey.MESSAGE_BOOK_DELETE_FAILED to "Le livre n'a pas pu être supprimé.",
    TextKey.MESSAGE_SAVED to "Enregistré.",
    TextKey.MESSAGE_VAULT_EXPORTED to "Coffre exporté. Conservez ce fichier en lieu sûr.",
    TextKey.MESSAGE_VAULT_IMPORTED to "Coffre importé. Vos livres et votre vocabulaire sont restaurés.",
    TextKey.MESSAGE_VAULT_EXPORT_FAILED to "Le coffre n'a pas pu être exporté.",
    TextKey.MESSAGE_NOT_A_VAULT to "Ce fichier n'est pas un export de coffre Lekto.",
    TextKey.MESSAGE_VAULT_UNREADABLE to
        "Ce fichier de coffre ne peut pas être lu par cette version de l'application.",
    TextKey.MESSAGE_VAULT_IMPORT_FAILED to "Le coffre n'a pas pu être importé.",
    TextKey.MESSAGE_PROVIDER_CONNECTED to "Le fournisseur a répondu. Votre modèle est prêt.",
    TextKey.MESSAGE_KEY_REMOVED to "Clé supprimée du stockage sécurisé.",
    TextKey.MESSAGE_SYNC_CONNECTED to "Le serveur a répondu. La synchronisation est prête.",
    TextKey.MESSAGE_SYNC_DISCONNECTED to "Déconnecté. Votre coffre reste sur cet appareil.",
    TextKey.MESSAGE_SYNC_NOT_CONFIGURED to "Ajoutez d'abord une adresse de serveur et un nom d'utilisateur.",
    TextKey.MESSAGE_SYNC_NO_PASSWORD to "Ajoutez d'abord le mot de passe d'application.",
    TextKey.MESSAGE_SYNC_PROBE_FAILED to "Impossible d'atteindre le serveur : {0}",
    TextKey.MESSAGE_SYNC_PROBE_FAILED_UNKNOWN to "Impossible d'atteindre le serveur : une erreur inconnue",
    TextKey.MESSAGE_SYNC_RUN_FAILED to "La dernière synchronisation ne s'est pas terminée : {0}",
    TextKey.MESSAGE_SYNC_RUN_FAILED_UNKNOWN to
        "La dernière synchronisation ne s'est pas terminée : une erreur inconnue",
    TextKey.MESSAGE_DICTIONARY_DOWNLOAD_FAILED to "Le téléchargement du dictionnaire a échoué.",
    TextKey.MESSAGE_DIFFICULTY_DOWNLOAD_FAILED to "Le téléchargement a échoué.",
    TextKey.MESSAGE_LANGUAGES_LOAD_FAILED to "La liste des langues n'a pas pu être chargée.",

    // The last sync's outcome.
    TextKey.REPORT_SYNCED to "Synchronisé : {0}.",
    TextKey.REPORT_NOTHING to "Rien n'a été synchronisé.",
    TextKey.REPORT_UP_TO_DATE to "Tout était déjà à jour.",
    TextKey.REPORT_DOWNLOADED to "{0} téléchargé(s)",
    TextKey.REPORT_UPLOADED to "{0} envoyé(s)",
    TextKey.REPORT_REMOVED to "{0} supprimé(s)",
    TextKey.REPORT_CARRIED_ONE to "{0} original de livre transporté",
    TextKey.REPORT_CARRIED_MANY to "{0} originaux de livres transportés",
    TextKey.REPORT_RESYNCED to "Cet appareil était absent depuis longtemps ; il est reparti du serveur.",
    TextKey.REPORT_UNSETTLED to
        "Certaines modifications n'ont pas pu être réglées ; elles seront réessayées la prochaine fois.",

    // Domain outcomes.
    TextKey.CORE_SECRET_UNAVAILABLE to "Le stockage sécurisé n'est pas disponible sur cet appareil.",
    TextKey.CORE_LLM_INCOMPLETE to "Choisissez un fournisseur et un modèle avant de tester la connexion.",
    TextKey.CORE_LLM_UNREACHABLE to
        "Le fournisseur n'a pas pu être atteint. Vérifiez votre connexion et l'URL de base.",
    TextKey.CORE_LLM_REJECTED_KEY to "Le fournisseur a refusé la clé API.",
    TextKey.CORE_LLM_NOT_FOUND to
        "Le fournisseur n'a pas de point d'accès à cette adresse. Vérifiez l'URL de base.",
    TextKey.CORE_LLM_RATE_LIMITED to "Le fournisseur limite le débit. Réessayez bientôt.",
    TextKey.CORE_LLM_BAD_REQUEST to "Le fournisseur a refusé la requête. Vérifiez le nom du modèle.",
    TextKey.CORE_LLM_UNKNOWN_MODEL to "Le fournisseur ne propose pas ce modèle. Vérifiez le nom du modèle.",
    TextKey.CORE_LLM_MISSING_SESSION to
        "Le fournisseur exige que l'application identifie sa session. Mettez à jour l'application ou " +
        "choisissez un autre fournisseur.",
    TextKey.CORE_LLM_NO_FUNDS to
        "Le compte du fournisseur ne peut pas payer la requête. Vérifiez votre facturation.",
    TextKey.CORE_LLM_SERVER_ERROR to "Le fournisseur a répondu par une erreur. Réessayez bientôt.",
    TextKey.CORE_MODELS_INCOMPLETE to "Choisissez un fournisseur avant de charger ses modèles.",
    TextKey.CORE_MODELS_MISSING_KEY to "Saisissez votre clé API pour charger les modèles de ce fournisseur.",
    TextKey.CORE_MODELS_UNSUPPORTED to "Ce fournisseur ne liste pas ses modèles. Saisissez le nom du modèle.",
    TextKey.CORE_MODELS_EMPTY to "Ce fournisseur n'a listé aucun modèle. Saisissez le nom du modèle.",
    TextKey.CORE_DICTIONARY_NOT_INSTALLED to "Le dictionnaire hors ligne n'est pas encore installé.",
    TextKey.CORE_DICTIONARY_UNKNOWN_LANGUAGE to
        "La langue de ce livre n'est pas connue ; le dictionnaire hors ligne ne peut pas être interrogé.",
    TextKey.CORE_SPEECH_UNKNOWN_LANGUAGE to
        "La langue de ce livre n'est pas connue ; aucune voix n'est disponible.",
    TextKey.CORE_SPEECH_NO_ENGINE to "Aucun moteur vocal n'est disponible sur cet appareil.",
    TextKey.CORE_SPEECH_FAILED to "Le moteur vocal n'a pas pu prononcer ce mot.",
    TextKey.CORE_SPEECH_STARTING to "Le moteur vocal démarre encore. Réessayez dans un instant.",
)

private val FrenchLanguageNames: Map<String, String> = mapOf(
    "ar" to "arabe",
    "cs" to "tchèque",
    "da" to "danois",
    "de" to "allemand",
    "el" to "grec",
    "en" to "anglais",
    "es" to "espagnol",
    "fi" to "finnois",
    "fr" to "français",
    "he" to "hébreu",
    "hi" to "hindi",
    "hu" to "hongrois",
    "id" to "indonésien",
    "it" to "italien",
    "ja" to "japonais",
    "ko" to "coréen",
    "nl" to "néerlandais",
    "no" to "norvégien",
    "pl" to "polonais",
    "pt" to "portugais",
    "ro" to "roumain",
    "ru" to "russe",
    "sv" to "suédois",
    "th" to "thaï",
    "tr" to "turc",
    "uk" to "ukrainien",
    "vi" to "vietnamien",
    "zh" to "chinois",
)
