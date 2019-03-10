/**
 * @depends {brs.js}
 * @depends {brs.modals.js}
 */
var BRS = (function(BRS, $, undefined) {
    $("#sign_message_modal").on("show.bs.modal", function(e) {
        $("#sign_message_output, #verify_message_output").html("").hide();

        $("#sign_message_modal_sign_message").show();
        $("#sign_message_modal_button").text("Sign Message").data("form", "sign_message_form");
    });

    BRS.forms.signModalButtonClicked = function() {
		if ($("#sign_message_nav").hasClass("active")) {
			BRS.forms.signMessage();
		} else {
		    BRS.forms.verifyMessage();
		}
    };

    BRS.forms.signMessage = function() {
        var isHex = $("#sign_message_data_is_hex").is(":checked");
        var data = $("#sign_message_data").val();
        var passphrase = converters.stringToHexString($("#sign_message_passphrase").val());
        if (!isHex) data = converters.stringToHexString(data);
        BRS.sendRequest("parseTransaction", { "transactionBytes": data }, function(result) {
            console.log(result);
            if (result.errorCode == null) {
                $("#sign_message_error").text("WARNING: YOU ARE SIGNING A TRANSACTION. IF YOU WERE NOT TRYING TO SIGN A TRANSACTION MANUALLY, DO NOT GIVE THIS SIGNATURE OUT. IT COULD ALLOW OTHERS TO SPEND YOUR FUNDS.");
                $("#sign_message_error").show();
            }
            signature = BRS.signBytes(data, passphrase);
            $("#sign_message_output").text("Signature is " + signature + ". Your public key is " + BRS.getPublicKey(passphrase));
            $("#sign_message_output").show();
        }, false);
    };

    BRS.forms.verifyMessage = function() {
        var isHex = $("#verify_message_data_is_hex").is(":checked");
        var data = $("#verify_message_data").val();
        var signature = $.trim($("#verify_message_signature").val());
        var publicKey = $.trim($("#verify_message_public_key").val());
        if (!isHex) data = converters.stringToHexString(data);
        var result = BRS.verifyBytes(signature, data, publicKey);
        if (result) {
            $("#verify_message_error").hide();
            $("#verify_message_output").text("Signature is valid");
            $("#verify_message_output").show();
        } else {
            $("#verify_message_output").hide();
            $("#verify_message_error").text("Signature is invalid");
            $("#verify_message_error").show();
        }
    };

    $("#sign_message_modal ul.nav li").click(function(e) {
        e.preventDefault();

        var tab = $(this).data("tab");

        $(this).siblings().removeClass("active");
        $(this).addClass("active");

        $(".sign_message_modal_content").hide();

        var content = $("#sign_message_modal_" + tab);

        if (tab === "sign_message") {
            $("#sign_message_modal_button").text("Sign Message").data("form", "sign_message_form");
        }
        else {
            $("#sign_message_modal_button").text("Verify Message").data("form", "verify_message_form");
        }

        $("#sign_message_modal .error_message").hide();

        content.show();
    });

    $("#sign_message_modal").on("hidden.bs.modal", function(e) {
        $(this).find(".sign_message_modal_content").hide();
        $(this).find("ul.nav li.active").removeClass("active");
        $("#sign_message_nav").addClass("active");
    });

    return BRS;
}(BRS || {}, jQuery));
