package com.zerobounce.android

import com.google.gson.annotations.SerializedName

data class ZBGetApiUsageResponse(
    // Total number of times the API has been called
    @SerializedName("total") val total: Int = 0,

    // Total valid email addresses returned by the API
    @SerializedName("status_valid") val statusValid: Int = 0,

    // Total invalid email addresses returned by the API
    @SerializedName("status_invalid") val statusInvalid: Int = 0,

    // Total catch-all email addresses returned by the API
    @SerializedName("status_catch_all") val statusCatchAll: Int = 0,

    // Total do not mail email addresses returned by the API
    @SerializedName("status_do_not_mail") val statusDoNotMail: Int = 0,

    // Total spamtrap email addresses returned by the API
    @SerializedName("status_spamtrap") val statusSpamtrap: Int = 0,

    // Total unknown email addresses returned by the API
    @SerializedName("status_unknown") val statusUnknown: Int = 0,

    // Total number of times the API has a sub status of "toxic"
    @SerializedName("sub_status_toxic") val subStatusToxic: Int = 0,

    // Total number of times the API has a sub status of "disposable"
    @SerializedName("sub_status_disposable") val subStatusDisposable: Int = 0,

    // Total number of times the API has a sub status of "role_based"
    @SerializedName("sub_status_role_based") val subStatusRoleBased: Int = 0,

    // Total number of times the API has a sub status of "possible_trap"
    @SerializedName("sub_status_possible_trap") val subStatusPossibleTrap: Int = 0,

    // Total number of times the API has a sub status of "global_suppression"
    @SerializedName("sub_status_global_suppression") val subStatusGlobalSuppression: Int = 0,

    // Total number of times the API has a sub status of "timeout_exceeded"
    @SerializedName("sub_status_timeout_exceeded") val subStatusTimeoutExceeded: Int = 0,

    // Total number of times the API has a sub status of "mail_server_temporary_error"
    @SerializedName("sub_status_mail_server_temporary_error") val subStatusMailServerTemporaryError: Int = 0,

    // Total number of times the API has a sub status of "mail_server_did_not_respond"
    @SerializedName("sub_status_mail_server_did_not_respond") val subStatusMailServerDidNotResponse: Int = 0,

    // Total number of times the API has a sub status of "greylisted"
    @SerializedName("sub_status_greylisted") val subStatusGreyListed: Int = 0,

    // Total number of times the API has a sub status of "antispam_system"
    @SerializedName("sub_status_antispam_system") val subStatusAntiSpamSystem: Int = 0,

    // Total number of times the API has a sub status of "does_not_accept_mail"
    @SerializedName("sub_status_does_not_accept_mail") val subStatusDoesNotAcceptMail: Int = 0,

    // Total number of times the API has a sub status of "exception_occurred"
    @SerializedName("sub_status_exception_occurred") val subStatusExceptionOccurred: Int = 0,

    // Total number of times the API has a sub status of "failed_syntax_check"
    @SerializedName("sub_status_failed_syntax_check") val subStatusFailedSyntaxCheck: Int = 0,

    // Total number of times the API has a sub status of "mailbox_not_found"
    @SerializedName("sub_status_mailbox_not_found") val subStatusMailboxNotFound: Int = 0,

    // Total number of times the API has a sub status of "unroutable_ip_address"
    @SerializedName("sub_status_unroutable_ip_address") val subStatusUnRoutableIpAddress: Int = 0,

    // Total number of times the API has a sub status of "possible_typo"
    @SerializedName("sub_status_possible_typo") val subStatusPossibleTypo: Int = 0,

    // Total number of times the API has a sub status of "no_dns_entries"
    @SerializedName("sub_status_no_dns_entries") val subStatusNoDnsEntries: Int = 0,

    // Total role based catch alls the API has a sub status of "role_based_catch_all"
    @SerializedName("sub_status_role_based_catch_all") val subStatusRoleBasedCatchAll: Int = 0,

    // Total number of times the API has a sub status of "mailbox_quota_exceeded"
    @SerializedName("sub_status_mailbox_quota_exceeded") val subStatusMailboxQuotaExceeded: Int = 0,

    // Total forcible disconnects the API has a sub status of "forcible_disconnect"
    @SerializedName("sub_status_forcible_disconnect") val subStatusForcibleDisconnect: Int = 0,

    // Total failed SMTP connections the API has a sub status of "failed_smtp_connection"
    @SerializedName("sub_status_failed_smtp_connection") val subStatusFailedSmtpConnection: Int = 0,

    // Start date of query
    @SerializedName("start_date") val startDate: String? = null,

    // End date of query
    @SerializedName("end_date") val endDate: String? = null
) : JSONConvertable