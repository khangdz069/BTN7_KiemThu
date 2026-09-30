using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai03
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai03.csv",
            "Bai03#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai03.csv", "data_csv")]

        [TestMethod]
        public void TestBinToDec()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string sbin = Convert.ToString(TestContext.DataRow[0]);
            long ketqua_mongdoi = Convert.ToInt64(TestContext.DataRow[1]);

            long ketqua_thucte = m.BinToDec(sbin);

            Assert.AreEqual(ketqua_mongdoi, ketqua_thucte);
        }
    }
}