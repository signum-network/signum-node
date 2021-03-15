/**
 * @depends {brs.js}
 */
var BRS = (function(BRS, $, undefined) {
    var radio = document.request_burst_form.request_burst_suggested_fee;
    $('#request_burst_qr_modal').on('show.bs.modal', function (e) {
       $("#new_qr_button").hide();
       $("#request_burst_immutable").prop('checked', true);
       $("#request_burst_account_id").val(String(BRS.accountRS).escapeHTML());

              var array = ["standard","cheap","priority"];
              for(var i = 0; i < radio.length; i++) {
                  radio[i].value = array[i];
                  radio[i].onclick = function() {
                       $("#request_burst_fee").val("");
                       $("#request_burst_fee_div").toggleClass("has-error",false);
                       $("#request_burst_fee_div").toggleClass("has-success",false);
                  };
              }

        BRS.sendRequest("suggestFee", {
                 }, function(response) {
                     if (!response.errorCode) {
                         $("#standard_fee_response").html("<span class='margin-left-5'>(<a href='#' class='btn-fee-response' name='fee_value' data-i18n='[title]click_to_apply'>" +(response.standard/100000000).toFixed(8)+ "</a>)</span>");
                         $("#cheap_fee_response").html("<span class='margin-left-5'>(<a href='#' class='btn-fee-response' name='fee_value' data-i18n='[title]click_to_apply'>" + (response.cheap/100000000).toFixed(8)+ "</a>)</span>");
                         $("#priority_fee_response").html("<span class='margin-left-5'>(<a href='#' class='btn-fee-response' name='fee_value' data-i18n='[title]click_to_apply'>" +(response.priority/100000000).toFixed(8)+ "</a>)</span>");
                         $("[name='fee_value']").i18n(); // apply locale to DOM after ajax call
                         $("[name='fee_value']").on("click", function(e) {
                                   e.preventDefault();
                                   $("#request_burst_fee").val($(this).text());
                            });
                     }
                     else {
                      $("#suggested_fee_response").html(response.errorDescription);
                      $("[name='suggested_fee_spinner']").addClass("suggested_fee_spinner_display_none");
                      }
                 });
    });
        $("#request_burst_amount").change(function() {
           var amount = Number($("#request_burst_amount").val());
           $("#request_burst_amount").val(amount);
           if(amount >= 0.00000001 || (!$("#request_burst_immutable").is(':checked') && (!amount || amount == 0)))
           {
               $("#request_burst_amount_div").toggleClass("has-error",false);
               $("#request_burst_amount_div").toggleClass("has-success",true);
           }
           else
           {
              $("#request_burst_amount_div").toggleClass("has-success",false);
              $("#request_burst_amount_div").toggleClass("has-error",true);
           }
         });

       $("#request_burst_fee").change(function() {
         var fee = Number($("#request_burst_fee").val());
         $("#request_burst_fee").val(fee);
         if(fee >= 0.00735)
         {
             for(var i = 0; i < radio.length; i++) {
               radio[i].checked = false;
             }
             $("#request_burst_fee_div").toggleClass("has-error",false);
             $("#request_burst_fee_div").toggleClass("has-success",true);
         }
         else
         {
               $("#request_burst_fee_div").toggleClass("has-success",false);
               $("#request_burst_fee_div").toggleClass("has-error",true);
         }
       });

       $('#request_burst_immutable').change(function() {
        var amount = Number($("#request_burst_amount").val());
        if($(this).is(":checked")) {
             if(amount >= 0.00000001){
                $("#request_burst_amount_div").toggleClass("has-error",false);
                $("#request_burst_amount_div").toggleClass("has-success",true);
             }
             else
             {
                $("#request_burst_amount_div").toggleClass("has-success",false);
                $("#request_burst_amount_div").toggleClass("has-error",true);
             }

           }
           else
           {
            if(amount >= 0.00000001 || (!amount || amount == 0)){
                $("#request_burst_amount_div").toggleClass("has-error",false);
                $("#request_burst_amount_div").toggleClass("has-success",true);
            }
            else
            {
                $("#request_burst_amount_div").toggleClass("has-success",false);
                $("#request_burst_amount_div").toggleClass("has-error",true);
            }

           }
       });

         $("#generate_qr_button").on("click", function(e) {
            e.preventDefault();
            var amount = Number($("#request_burst_amount").val());
            if(((!amount || amount < 0.00000001) && $("#request_burst_immutable").is(':checked')) || (amount && amount < 0.00000001))
            {
              $("#request_burst_amount_div").toggleClass("has-success",false);
              $("#request_burst_amount_div").toggleClass("has-error",true);
              return;
            }
            else
            {
              $("#request_burst_amount_div").toggleClass("has-error",false);
              $("#request_burst_amount_div").toggleClass("has-success",true);
            }
            var fee = Number($("#request_burst_fee").val());
            for(var i = 0; i < radio.length; i++) {
              if(radio[i].checked == true)
              var suggested_fee = radio[i].value;
            }
            if((!fee || fee < 0.00735) && !suggested_fee)
            {
              $("#request_burst_fee_div").toggleClass("has-success",false);
              $("#request_burst_fee_div").toggleClass("has-error",true);
              return;
            }
            else
            {
              $("#request_burst_fee_div").toggleClass("has-error",false);
              $("#request_burst_fee_div").toggleClass("has-success",true);
            }
            var amountNQT =  amount * 100000000;
            var feeNQT = fee * 100000000;
            var receiverId = BRS.accountRS;
            if($("#request_burst_immutable").is(':checked')){
                immutable = "&immutable=true";
                $("#request_burst_immutable_response").html("Yes");
            }
            else{
                immutable = "";
                $("#request_burst_immutable_response").html("No");
            }
            if(suggested_fee){
                $("#request_burst_qrcode_response").html('<img src="/burst?requestType=generateSendTransactionQRCode&receiverId='+receiverId+'&amountNQT='+amountNQT+'&feeSuggestionType='+suggested_fee+immutable+'"/>');
                $("#request_burst_fee_response").html(suggested_fee.charAt(0).toUpperCase() + suggested_fee.slice(1));
            }
            else{
                $("#request_burst_qrcode_response").html('<img src="/burst?requestType=generateSendTransactionQRCode&receiverId='+receiverId+'&amountNQT='+amountNQT+'&feeNQT='+feeNQT+immutable+'"/>');
                $("#request_burst_fee_response").html($("#request_burst_fee").val());
            }
            $("#generate_qr_button").hide();
            $("#new_qr_button").show();
            $("#cancel_button").html('Close');
            $("#request_burst_recipient_response").html(receiverId);
            if($("#request_burst_amount").val())
            $("#request_burst_amount_response").html($("#request_burst_amount").val() + " BURST");
            $("#request_burst_div").removeClass("display-visible");
            $("#request_burst_div").addClass("display-none");
            $("#request_burst_response_div").removeClass("display-none");
            $("#request_burst_response_div").addClass("display-visible");
        });


    $('#request_burst_qr_modal').on('hide.bs.modal', function (e) {
        $("#request_burst_div").removeClass("display-none");
        $("#request_burst_div").addClass("display-visible");
        $("#request_burst_response_div").removeClass("display-visible");
        $("#request_burst_response_div").addClass("display-none");
        $("#request_burst_amount_div").toggleClass("has-error",false);
        $("#request_burst_amount_div").toggleClass("has-success",false);
        $("#request_burst_fee_div").toggleClass("has-success",true);
        $("#request_burst_fee_div").toggleClass("has-error",false);
        var radio = document.request_burst_form.request_burst_suggested_fee;
        for(var i = 0; i < radio.length; i++) {
            radio[i].checked = false;
        }
        $("#cancel_button").html('Cancel');
        $("#generate_qr_button").show();
     });

    $("#new_qr_button").on("click", function(e) {
        $("#request_burst_div").removeClass("display-none");
        $("#request_burst_div").addClass("display-visible");
        $("#request_burst_response_div").removeClass("display-visible");
        $("#request_burst_response_div").addClass("display-none");
        $("#request_burst_amount_div").toggleClass("has-error",false);
        $("#request_burst_amount_div").toggleClass("has-success",false);
        $("#request_burst_fee_div").toggleClass("has-success",true);
        $("#request_burst_fee_div").toggleClass("has-error",false);
        $("#request_burst_amount").val("");
        $("#request_burst_fee").val(0.1);
        var radio = document.request_burst_form.request_burst_suggested_fee;
        for(var i = 0; i < radio.length; i++) {
         radio[i].checked = false;
        }
        $("#request_burst_immutable").prop('checked', true);
        $("#cancel_button").html('Cancel');
        $("#generate_qr_button").show();
        $("#new_qr_button").hide();
    });

    return BRS;
}(BRS || {}, jQuery));
