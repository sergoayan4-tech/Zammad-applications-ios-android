import Foundation

/// Tiny in app localization engine.
///
/// The app ships two complete tables (English and Russian). The user can switch
/// language in the settings without restarting the app, which is why strings are
/// resolved through `AppPreferences.t(_:)` inside view bodies.
enum L10n {
    /// Fallback code used when no preferences object is available.
    static var currentCode: String = "en"

    static func translate(_ key: String, code: String) -> String {
        if code == "ru", let value = Strings.ru[key] {
            return value
        }
        return Strings.en[key] ?? key
    }

    static func t(_ key: String) -> String {
        translate(key, code: currentCode)
    }

    enum Strings {
        static let en: [String: String] = [
            // Common
            "common.loading": "Loading…",
            "common.cancel": "Cancel",
            "common.ok": "OK",
            "common.retry": "Try again",
            "common.close": "Close",
            "common.save": "Save",
            "common.done": "Done",
            "common.unknown": "Unknown",

            // Connection screen
            "connect.title": "Connect to Zammad",
            "connect.subtitle": "Enter your helpdesk address and sign in",
            "connect.server": "Server address",
            "connect.server.placeholder": "https://help.example.com",
            "connect.auth": "Authorization",
            "connect.auth.token": "API token",
            "connect.auth.password": "Login and password",
            "connect.token": "Personal access token",
            "connect.token.hint": "Create it in Zammad: Profile → Personal access tokens.",
            "connect.username": "Login",
            "connect.password": "Password",
            "connect.password.hint": "The password is exchanged for an API token when the server allows it, otherwise HTTP Basic is used.",
            "connect.submit": "Connect",
            "connect.connecting": "Connecting…",
            "connect.error.url": "Enter the server address, e.g. https://help.example.com",
            "connect.error.token": "Enter your API token",
            "connect.error.credentials": "Enter your login and password",

            // Tabs
            "tab.tickets": "Tickets",
            "tab.new": "New",
            "tab.settings": "Settings",

            // Ticket list
            "tickets.title": "Tickets",
            "tickets.search": "Search tickets",
            "tickets.filter.all": "All",
            "tickets.filter.open": "Open",
            "tickets.filter.mine": "Mine",
            "tickets.filter.closed": "Closed",
            "tickets.empty": "No tickets",
            "tickets.empty.hint": "Pull the list to refresh or change the filter.",
            "tickets.empty.search": "Nothing found",
            "tickets.empty.search.hint": "Try another search request.",
            "tickets.loadMore": "Load more",
            "tickets.refresh": "Refresh",

            // Ticket detail
            "ticket.state": "State",
            "ticket.priority": "Priority",
            "ticket.group": "Group",
            "ticket.customer": "Customer",
            "ticket.owner": "Owner",
            "ticket.organization": "Organization",
            "ticket.created": "Created",
            "ticket.updated": "Updated",
            "ticket.messages": "Messages",
            "ticket.noMessages": "No messages yet.",
            "ticket.actions": "Ticket actions",
            "ticket.changeState": "Change state",
            "ticket.changePriority": "Change priority",
            "ticket.changeGroup": "Change group",
            "ticket.assign": "Assign to",
            "ticket.assign.me": "Assign to me",
            "ticket.assign.unassigned": "Unassigned",
            "ticket.assign.search": "Search agents",
            "ticket.assign.empty": "No agents found",

            // Composer
            "reply.public": "Public reply",
            "reply.internal": "Internal note",
            "reply.placeholder": "Write a message…",
            "reply.send": "Send",
            "reply.sending": "Sending…",
            "reply.internalHint": "Visible only to agents",

            // Articles
            "article.internal": "Internal",

            // New ticket
            "new.title": "New ticket",
            "new.subject": "Subject",
            "new.subject.placeholder": "How can we help?",
            "new.group": "Group",
            "new.customer": "Customer email",
            "new.customer.placeholder": "customer@example.com",
            "new.priority": "Priority",
            "new.state": "State",
            "new.message": "First message",
            "new.message.placeholder": "Describe the request…",
            "new.submit": "Create ticket",
            "new.creating": "Creating…",
            "new.error.title": "Enter the subject",
            "new.error.group": "Choose a group",
            "new.error.message": "Enter the message text",

            // Settings
            "settings.title": "Settings",
            "settings.language": "Language",
            "settings.language.system": "System",
            "settings.appearance": "Appearance",
            "settings.appearance.system": "System",
            "settings.appearance.light": "Light",
            "settings.appearance.dark": "Dark",
            "settings.connection": "Connection",
            "settings.server": "Server",
            "settings.account": "Account",
            "settings.auth": "Authorization",
            "settings.disconnect": "Disconnect",
            "settings.disconnect.question": "Disconnect from the server? The saved credentials will be removed.",
            "settings.about": "About",
            "settings.version": "Version",
            "settings.api": "API documentation",

            // Errors
            "error.network": "No connection to the server. Check the address and your network.",
            "error.unauthorized": "Authorization failed. Check your token, login or password.",
            "error.forbidden": "This account has no access rights.",
            "error.notFound": "Not found on the server.",
            "error.server": "The server returned an error.",
            "error.request": "Request failed.",
            "error.decode": "Could not read the server response.",
            "error.invalidURL": "Invalid server address.",
            "error.unknown": "Something went wrong."
        ]

        static let ru: [String: String] = [
            // Common
            "common.loading": "Загрузка…",
            "common.cancel": "Отмена",
            "common.ok": "ОК",
            "common.retry": "Повторить",
            "common.close": "Закрыть",
            "common.save": "Сохранить",
            "common.done": "Готово",
            "common.unknown": "Неизвестно",

            // Connection screen
            "connect.title": "Подключение к Zammad",
            "connect.subtitle": "Укажите адрес хелпдеска и войдите в систему",
            "connect.server": "Адрес сервера",
            "connect.server.placeholder": "https://help.example.com",
            "connect.auth": "Авторизация",
            "connect.auth.token": "API-токен",
            "connect.auth.password": "Логин и пароль",
            "connect.token": "Персональный токен",
            "connect.token.hint": "Создаётся в Zammad: Профиль → Персональные токены.",
            "connect.username": "Логин",
            "connect.password": "Пароль",
            "connect.password.hint": "Пароль обменивается на API-токен, если сервер это разрешает; иначе используется HTTP Basic.",
            "connect.submit": "Подключиться",
            "connect.connecting": "Подключение…",
            "connect.error.url": "Укажите адрес сервера, например https://help.example.com",
            "connect.error.token": "Укажите API-токен",
            "connect.error.credentials": "Укажите логин и пароль",

            // Tabs
            "tab.tickets": "Тикеты",
            "tab.new": "Создать",
            "tab.settings": "Настройки",

            // Ticket list
            "tickets.title": "Тикеты",
            "tickets.search": "Поиск тикетов",
            "tickets.filter.all": "Все",
            "tickets.filter.open": "Открытые",
            "tickets.filter.mine": "Мои",
            "tickets.filter.closed": "Закрытые",
            "tickets.empty": "Нет тикетов",
            "tickets.empty.hint": "Потяните список для обновления или смените фильтр.",
            "tickets.empty.search": "Ничего не найдено",
            "tickets.empty.search.hint": "Попробуйте другой запрос.",
            "tickets.loadMore": "Показать ещё",
            "tickets.refresh": "Обновить",

            // Ticket detail
            "ticket.state": "Статус",
            "ticket.priority": "Приоритет",
            "ticket.group": "Группа",
            "ticket.customer": "Клиент",
            "ticket.owner": "Ответственный",
            "ticket.organization": "Организация",
            "ticket.created": "Создан",
            "ticket.updated": "Обновлён",
            "ticket.messages": "Сообщения",
            "ticket.noMessages": "Сообщений пока нет.",
            "ticket.actions": "Действия с тикетом",
            "ticket.changeState": "Изменить статус",
            "ticket.changePriority": "Изменить приоритет",
            "ticket.changeGroup": "Изменить группу",
            "ticket.assign": "Назначить",
            "ticket.assign.me": "Назначить на меня",
            "ticket.assign.unassigned": "Не назначен",
            "ticket.assign.search": "Поиск сотрудников",
            "ticket.assign.empty": "Сотрудники не найдены",

            // Composer
            "reply.public": "Публичный ответ",
            "reply.internal": "Внутренняя заметка",
            "reply.placeholder": "Напишите сообщение…",
            "reply.send": "Отправить",
            "reply.sending": "Отправка…",
            "reply.internalHint": "Видно только сотрудникам",

            // Articles
            "article.internal": "Внутр.",

            // New ticket
            "new.title": "Новый тикет",
            "new.subject": "Тема",
            "new.subject.placeholder": "Чем помочь?",
            "new.group": "Группа",
            "new.customer": "Email клиента",
            "new.customer.placeholder": "customer@example.com",
            "new.priority": "Приоритет",
            "new.state": "Статус",
            "new.message": "Первое сообщение",
            "new.message.placeholder": "Опишите запрос…",
            "new.submit": "Создать тикет",
            "new.creating": "Создание…",
            "new.error.title": "Укажите тему",
            "new.error.group": "Выберите группу",
            "new.error.message": "Укажите текст сообщения",

            // Settings
            "settings.title": "Настройки",
            "settings.language": "Язык",
            "settings.language.system": "Системный",
            "settings.appearance": "Оформление",
            "settings.appearance.system": "Системное",
            "settings.appearance.light": "Светлое",
            "settings.appearance.dark": "Тёмное",
            "settings.connection": "Подключение",
            "settings.server": "Сервер",
            "settings.account": "Аккаунт",
            "settings.auth": "Авторизация",
            "settings.disconnect": "Отключиться",
            "settings.disconnect.question": "Отключиться от сервера? Сохранённые данные входа будут удалены.",
            "settings.about": "О приложении",
            "settings.version": "Версия",
            "settings.api": "Документация API",

            // Errors
            "error.network": "Нет связи с сервером. Проверьте адрес и сеть.",
            "error.unauthorized": "Ошибка авторизации. Проверьте токен, логин или пароль.",
            "error.forbidden": "У аккаунта нет прав доступа.",
            "error.notFound": "Не найдено на сервере.",
            "error.server": "Сервер вернул ошибку.",
            "error.request": "Запрос не выполнен.",
            "error.decode": "Не удалось прочитать ответ сервера.",
            "error.invalidURL": "Некорректный адрес сервера.",
            "error.unknown": "Что-то пошло не так."
        ]
    }
}
