package com.zerobounce.android

import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * The model used for the GET /validate request.
 */
data class ZBValidateResponse(

    // The email address you are validating.
    val address: String? = null,

    // [valid, invalid, catch-all, unknown, spamtrap, abuse, do_not_mail]
    val status: ZBValidateStatus? = null,

    // [antispam_system, greylisted, mail_server_temporary_error, forcible_disconnect, mail_server_did_not_respond, timeout_exceeded, failed_smtp_connection, mailbox_quota_exceeded, exception_occurred, possible_traps, role_based, global_suppression, mailbox_not_found, no_dns_entries, failed_syntax_check, possible_typo, unroutable_ip_address, leading_period_removed, does_not_accept_mail, alias_address, role_based_catch_all, disposable, toxic]
    @SerializedName("sub_status")
    val subStatus: ZBValidateSubStatus? = null,

    // The portion of the email address before the "@" symbol.
    val account: String? = null,

    // The portion of the email address after the "@" symbol.
    val domain: String? = null,

    // Suggestive Fix for an email typo
    @SerializedName("did_you_mean")
    val didYouMean: String? = null,

    //Age of the email domain in days or [null].
    @SerializedName("domain_age_days")
    val domainAgeDays: String? = null,

    // [true/false] If the email comes from a free provider.
    @SerializedName("free_email")
    val freeEmail: Boolean = false,

    // [true/false] Does the domain have an MX record.
    @SerializedName("mx_found")
    val mxFound: Boolean = false,

    // The preferred MX record of the domain
    @SerializedName("mx_record")
    val mxRecord: String? = null,

    // The SMTP Provider of the email or [null] [BETA].
    @SerializedName("smtp_provider")
    val smtpProvider: String? = null,

    // The first name of the owner of the email when available or [null].
    @SerializedName("firstname")
    val firstName: String? = null,

    // The last name of the owner of the email when available or [null].
    @SerializedName("lastname")
    val lastName: String? = null,

    // The gender of the owner of the email when available or [null].
    val gender: String? = null,

    // The city of the IP passed in.
    val city: String? = null,

    // The region/state of the IP passed in.
    val region: String? = null,

    // The zipcode of the IP passed in.
    @SerializedName("zipcode")
    val zipCode: String? = null,

    // The country of the IP passed in.
    val country: String? = null,

    // The UTC time the email was validated.
    @SerializedName("processed_at")
    val processedAt: Date? = null,

    val error: String? = null
) : JSONConvertable